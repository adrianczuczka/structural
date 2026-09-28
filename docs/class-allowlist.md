# Class allowlist

[← README](../README.md)

Real codebases always seem to have a few cases where one specific class needs to cross a boundary
that the package rules don't allow — a shared exception, a builder, a handful of internals you're
mid-refactor on. The optional `classAllowlist:` section is for those: it lets you punch a
class-shaped hole through a package rule without weakening the package rule itself.

The name says it: it's an *allowlist*, not a constraint. Entries here grant cross-package imports
that package rules would otherwise reject; they never take away an import that package rules
already allow. Use them sparingly, and prefer fixing the package boundary if the list starts
growing.

```yaml
rules:
  # bare entries — api and impl are tracked, but no cross-package imports are allowed
  - com.example.api
  - com.example.impl

classAllowlist:
  - "com.example.api.** <- com.example.impl.FusionException"
  - "com.example.api.ApiBuilder <- com.example.impl.**"
  - "com.example.api.** <- com.example.impl._Private_*"
  - "com.example.api.** <- com.example.impl.**._Private_*"
```

Each package pattern in a `classAllowlist:` entry must overlap at least one tracked package in
`rules:` — there must be a concrete package that matches both. For example, `com.*.api` overlaps
`com.foo.*` because both match `com.foo.api`. Structural refuses to load the config if either side
has no overlap. It warns about a redundant entry only when package rules already permit every
possible tracked package combination that the entry can match.

### Token grammar

Each side of a class rule is a token like `com.example.api.ApiBuilder`. Structural splits it into a
**package portion** and an optional **class portion**, working through these rules in order:

1. Any segment containing `*` (other than the whole-segment wildcards `*` and `**`) is the class
   name. So `com.example.impl._Private_*` parses as package `com.example.impl`, class
   `_Private_*`.
2. Otherwise, the first segment whose first non-underscore character is uppercase is the class
   name. So `com.example.api.ApiBuilder` parses as package `com.example.api`, class `ApiBuilder`.
   `_PrivateClass` counts because the first non-underscore character (`P`) is uppercase.
3. If neither of those matches (i.e. everything is lowercase), the whole token is a package
   pattern. `com.example.api.**` is package=`com.example.api.**`, class=any.
4. For the rare lowercase class name (a Kotlin typealias, a DSL receiver, etc.), prefix the
   trailing segment with `:` to force it: `com.example.api.:listOf` parses as package
   `com.example.api`, class `listOf`.

The package portion uses the [package glob grammar](configuration.md#glob-patterns). A single-segment class-rule
prefix is literal: `api.Client` matches `Client` in package `api`. To match `Client` in any package
containing an `api` segment, including its subpackages, use `**.api.**.Client`.

The class portion supports shell-style globs on a single identifier:

| Class pattern | Matches |
| --- | --- |
| `Foo` | exact match (case-sensitive) |
| `*Foo` | any name ending with `Foo` |
| `Foo*` | any name starting with `Foo` |
| `*Foo*` | any name containing `Foo` |
| `*` | any non-empty class name |

`**` is not a valid class-name pattern (class names are single identifiers); use the package
portion's `**` for cross-subpackage matching.

### Map form

Same map form as `rules:`, with the importer as the key:

```yaml
classAllowlist:
  "com.example.api.**":
    - com.example.impl.FusionException
    - com.example.impl._Private_*
  "com.example.api.ApiBuilder":
    - com.example.impl.**
```

### Known limitations

A few sharp edges worth knowing about up front:

- **The importing class's identity is its file name.** Structural is file-scoped, so a class rule
  on `com.example.api.ApiBuilder` matches a file called `ApiBuilder.kt`. If you've got an `Api.kt`
  that happens to *declare* `class ApiBuilder` inside it, the rule won't fire. The fix is usually
  to name the file after the class you care about.
- **Wildcard imports can't be granted by class rules.** `import com.foo.*` has no class name to
  match against, so class rules can't engage and the package-level rule applies as-is.
- **Java static imports are matched against the enclosing class.** So
  `import static com.foo.Util.LOG;` is granted by a rule on `com.foo.Util`, not one on
  `com.foo.LOG`.
- **Nested-class patterns aren't supported.** A token like `com.example.Foo.Bar` is rejected when
  the config is parsed. For types declared in the checked sources, importing `Foo.Bar` uses the
  declared package and the simple name `Bar` for class rules; a static import of `Foo.Bar.member`
  also uses `Bar`. A rule on `com.example.Bar` can grant these imports, but cannot distinguish
  nested classes with the same simple name in different enclosing types.
- **Kotlin object members.** `import com.foo.MyObject.member` is matched by simple name
  (`member`), not against the enclosing object. Kotlin's import directive doesn't tell us whether
  `member` is an object member or a top-level declaration, so treat them the same when writing
  rules.
- **Types outside the checked sources are not resolved.** Structural indexes top-level Java and
  Kotlin type declarations to identify the actual package of nested-type and member imports.
  For types available only in dependencies or other unchecked source sets, it falls back to
  removing the final import segment (two for Java static imports). Nested imports of those types
  can therefore still be mistaken for imports from a subpackage. The dependency classpath is
  not analyzed.
