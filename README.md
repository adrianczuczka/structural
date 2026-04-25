![https://github.com/adrianczuczka/structural/releases](https://img.shields.io/maven-central/v/com.adrianczuczka/structural)
![https://github.com/adrianczuczka/structural/issues](https://img.shields.io/github/issues/adrianczuczka/structural)
![https://github.com/adrianczuczka/structural/actions](https://img.shields.io/github/actions/workflow/status/adrianczuczka/structural/gradle.yml)

# Structural

![title image](images/readme_title_image.png)

---

A lightweight Gradle plugin for enforcing package dependency rules in Kotlin and Java projects.
Define which packages can import from others within your project and enforce it automatically.

Structural is intended to be a quick way to enforce a modular architecture when other tools are not
preferred or available. However, it can also be used for other purposes such as just forbidding one
local package from importing from another.

## Installation

```kts
plugins {
    id("com.adrianczuczka.structural") version "[version]"
}

repositories {
    mavenCentral()
}
```

## Usage

### Set the structure

Structural requires a YAML file to understand the intended package structure. First, create a YAML
file in your project. You can link it like this:

```kts
structural {
    config = "./structural.yml"
}
```

If you omit this, it will look for a `structural.yml` file by default in the root directory.

In your YAML file, there should be two main sections: `packages` and `rules`. `packages` lists the
package paths that Structural should check. For example, if you follow MVVM and Clean Architecture
rules, your list could look like this:

```yaml
packages:
  - com.example.app.local
  - com.example.app.remote
  - com.example.app.data
  - com.example.app.domain
  - com.example.app.ui
```

A bare path like `com.example.app.data` matches that path *and any of its subpackages* — so any
file under `com.example.app.data.**` is governed by `com.example.app.data`'s rules. When several
tracked packages match the same file, the most specific (longest) one wins.

The `rules` section specifies which packages can import from which others. You can either use
arrows:

```yaml
rules:
  - com.example.app.data <- com.example.app.domain -> com.example.app.ui
  - com.example.app.local <- com.example.app.data
  - com.example.app.remote <- com.example.app.data
```

`A <- B` reads "`A` may be imported from `B`" (data flows from `B` to `A`); equivalently `B -> A`.

So the rules above mean:

1. `com.example.app.data` and `com.example.app.ui` can import from `com.example.app.domain`, but not vice versa.
2. `com.example.app.local` and `com.example.app.remote` can import from `com.example.app.data`, but not vice versa.

The same rules can be written as a map (key is the importer):

```yaml
rules:
  com.example.app.ui:
    - com.example.app.domain
  com.example.app.data:
    - com.example.app.domain
  com.example.app.local:
    - com.example.app.data
  com.example.app.remote:
    - com.example.app.data
```

YAML composite keys also work for grouping importers that share the same allowlist:

```yaml
rules:
  ? [ com.example.app.ui, com.example.app.data ]
    :
    - com.example.app.domain

  ? [ com.example.app.local, com.example.app.remote ]
    :
    - com.example.app.data
```

### Single-segment shorthand

If your project's structure is simple enough, you can use single-segment names. Single-segment
tokens (no dots) are matched by *last segment only* — so a token of `data` matches any file whose
package ends in `.data` regardless of the prefix:

```yaml
packages:
  - local
  - remote
  - data
  - domain
  - ui

rules:
  - data <- domain -> ui
  - local <- data
  - remote <- data
```

Single-segment shorthand cannot use wildcards or `!`. If two packages in your project share the
same last segment (for instance `app1.data` and `app2.data`), use the multi-segment form so the
plugin can tell them apart.

### Glob patterns

For multi-segment (fully-qualified) package names, Structural supports Ant-style wildcards so you
don't have to enumerate every subpackage by hand:

| Pattern | Matches |
| --- | --- |
| `com.example.foo` | `com.example.foo` and any subpackage (the default) |
| `com.example.foo.**` | same as above — explicit form, useful for readability |
| `com.example.foo!` | **only** `com.example.foo`, no subpackages |
| `com.example.*` | direct children of `com.example` only (`com.example.foo`, not `com.example.foo.bar`) |
| `com.*.api` | any `com.X.api` where `X` is a single segment |
| `com.**.internal` | any package under `com.` that ends in `.internal`, plus `com.internal` itself |
| `**.private` | any package ending in `.private`, plus `private` itself |

Rules:

- `*` matches exactly one package segment.
- `**` matches zero or more segments.
- A trailing `!` means exact match and cannot be combined with wildcards.
- Wildcards and `!` are only valid on multi-segment (dotted) tokens — they are rejected on
  single-segment names like `data`.
- Each side of an arrow rule is parsed independently, so `A! -> B.**` is valid.

Example from the user-facing issue this feature was added for — letting everything under
`dev.ionfusion.runtime` import from anything under `dev.ionfusion.runtime._private`:

```yaml
packages:
  - dev.ionfusion.runtime._private
  - dev.ionfusion.runtime

rules:
  - dev.ionfusion.runtime._private -> dev.ionfusion.runtime
```

And if you wanted to lock the relationship down to the exact packages only (no subpackages on
either side):

```yaml
rules:
  - dev.ionfusion.runtime._private! -> dev.ionfusion.runtime!
```

### Class rules (additive)

Sometimes a single class needs to cross a package boundary that the package rules deny — a shared
exception type, a builder class, or a small handful of internals during a refactor. The optional
top-level `classes:` section lets you grant those specific imports without loosening the
package-level rules:

```yaml
packages:
  - com.example.api
  - com.example.impl

rules: []   # api and impl cannot import each other by default

classes:
  - "com.example.api.** <- com.example.impl.FusionException"
  - "com.example.api.ApiBuilder <- com.example.impl.**"
  - "com.example.api.** <- com.example.impl._Private_*"
  - "com.example.api.** <- com.example.impl.**._Private_*"
```

Class rules are **purely additive**: they can grant a cross-package import that package rules would
otherwise reject. They cannot deny what package rules allow. (A future release may add a
deny-exception form.)

#### Token grammar

Each side of a class rule is a token like `com.example.api.ApiBuilder`. The parser splits it into a
**package portion** and an optional **class portion** using these rules, in priority order:

1. A segment containing `*` (other than the whole-segment `*` and `**` package wildcards) is the
   class-name segment. So `com.example.impl._Private_*` parses as package `com.example.impl`, class
   `_Private_*`.
2. Otherwise, the first segment whose first non-underscore character is uppercase is the class
   name. So `com.example.api.ApiBuilder` parses as package `com.example.api`, class `ApiBuilder`.
   `_PrivateClass` is recognised as a class because the first non-underscore character (`P`) is
   uppercase.
3. Otherwise (all-lowercase token), there is no class portion — the whole token is a package
   pattern. So `com.example.api.**` is package=`com.example.api.**`, class=any.
4. As an escape for the rare case of a lowercase class name (e.g. a Kotlin typealias or DSL
   receiver), prefix the trailing segment with `:` to force it to be parsed as a class:
   `com.example.api.:listOf` parses as package `com.example.api`, class `listOf`.

The package portion uses the [glob grammar above](#glob-patterns); the class portion supports
shell-style globs on a single identifier:

| Class pattern | Matches |
| --- | --- |
| `Foo` | exact match (case-sensitive) |
| `*Foo` | any name ending with `Foo` |
| `Foo*` | any name starting with `Foo` |
| `*Foo*` | any name containing `Foo` |
| `*` | any non-empty class name |

`**` is not a valid class-name pattern (class names are single identifiers); use the package
portion's `**` for cross-subpackage matching.

#### Map form

Class rules also accept the same map form as `rules:` — the key is the importer:

```yaml
classes:
  "com.example.api.**":
    - com.example.impl.FusionException
    - com.example.impl._Private_*
  "com.example.api.ApiBuilder":
    - com.example.impl.**
```

#### Known limitations

- **Class identity is the file name.** Structural is file-scoped, so the importing class's identity
  is the file's name without extension (e.g. `ApiBuilder.kt` ⇒ class `ApiBuilder`). A Kotlin file
  named `Api.kt` that *declares* a class `ApiBuilder` will not match a rule referencing
  `com.example.api.ApiBuilder` — name your file after the class you want to constrain.
- **Wildcard imports cannot be granted by class rules.** `import com.foo.*` has no class name, so
  class rules can't engage; the package-level decision applies.
- **Static imports** (Java) are matched against the *enclosing class*. So
  `import static com.foo.Util.LOG;` is granted by a rule referencing `com.foo.Util`, not one
  referencing `com.foo.LOG`.
- **Nested-class patterns are not supported.** A token like `com.example.Foo.Bar` is rejected at
  parse time. A rule on `Foo` matches imports of `Foo.Bar` by simple name (`Bar`); use a class glob
  on the imported side if you need that.
- **Kotlin object members.** `import com.foo.MyObject.member` is matched by simple name (`member`),
  not against the enclosing object — there's no `isStatic` flag in Kotlin to disambiguate.

### Run the check

To check your project's package imports against the rules, run:

```bash
./gradlew structuralCheck 
```

An example result will look like this:
![readme_example_result.png](images/readme_example_result.png)

### Setting a baseline

To ignore certain issues, you can run this command:

```bash
./gradlew structuralGenerateBaseline
```

This will create a baseline file containing all current issues, which will be ignored on subsequent checks. By default,
the baseline will be created in `$rootDir/baseline.xml`. To set a custom path, you can add this property:

```kts
structural {
    baseline = "./baseline.xml"
}
```

### Compatibility

This plugin supports both Kotlin and Java source files. It uses the Kotlin compiler for parsing via an isolated classloader, so it works with any Kotlin version in your project.