# Configuration

[← README](../README.md)

Structural reads its configuration from a YAML file. By default it looks for `structural.yml` next
to your build file; if you'd rather keep it somewhere else, point at it explicitly:

```kts
structural {
    config.set(file("structural.yml"))
}
```

The required `rules:` section defines the layers you want to enforce, expressed as the
relationships between them. Every identifier that appears in `rules:` becomes a tracked layer; any
import from outside that set (kotlin stdlib, third-party libs, packages you don't care about) is
unconditionally allowed.

The supported top-level keys are `rules` and the optional `classAllowlist`. Keys are
case-sensitive; unknown keys fail with a configuration error. For example, `classAllowList`
is rejected with a suggestion to use `classAllowlist`.

For most projects, naming the layers by their last segment is all you need. The arrow form reads
naturally for short rule sets:

```yaml
rules:
  - data <- domain -> ui
  - local <- data
  - remote <- data
```

A token like `data` (no dots) matches packages with a `data` segment and their subpackages — for
example, `com.example.app.data` and `com.example.app.data.local`. That's almost always what you want for an
architectural rule like "nothing in `ui` may touch `data` directly."

`A <- B` means **`A` may import from `B`**; `B -> A` means the same thing. So the rules above say:

1. `data` and `ui` can import from `domain`, but not the other way around.
2. `local` and `remote` can import from `data`, but not the other way around.

To track a layer without adding import permissions, list it as a bare entry — no arrow needed.
For fully-qualified nested packages, enclosing package permissions still apply:

```yaml
rules:
  - data <- domain -> ui
  - legacy           # tracked, with no allowed imports from other layers
```

Once you have more than a handful of rules the arrow form gets noisy, and you'll probably want the
map form. The key is the importer:

```yaml
rules:
  ui:
    - domain
  data:
    - domain
  local:
    - data
  remote:
    - data
  legacy: []         # tracked, with no additional permissions
```

Map values accept dependency lists or the `allow`/`inherit` object form described below.
Use `[]` for a package with no additional permissions; leaving the value blank is a configuration
error. YAML comments start with `#`, so write `legacy: [] # No additional permissions`.
A value such as `legacy: // No additional permissions` is a string and is rejected.
Arrow rule entries, package keys, and dependency list items must also be strings.

Dependency targets become tracked packages throughout the project. Adding a broad target such as
`com.example.commons.**` can expose violations in other packages that previously imported from
untracked packages. Each importer needs a direct or inherited permission to import a tracked
dependency. Declaring the dependency with `[]` gives it no additional import permissions of its own.

If a bunch of packages share the same allowlist, YAML composite keys let you group them:

```yaml
rules:
  ? [ ui, data ]
    :
    - domain

  ? [ local, remote ]
    :
    - data
```

## Fully-qualified package names

The shorthand above breaks down in two situations: when two packages in your codebase share a last
segment (say `app1.data` and `app2.data` — the shorthand can't tell them apart), or when you want
to use wildcards (which aren't allowed on single-segment names). For either case, write the package
out in full:

```yaml
rules:
  - com.example.app.data <- com.example.app.domain -> com.example.app.ui
  - com.example.app.local <- com.example.app.data
  - com.example.app.remote <- com.example.app.data
```

A bare path like `com.example.app.data` matches that path *and any of its subpackages*, so anything
under `com.example.app.data.**` lives by `com.example.app.data`'s rules. When more than one tracked
package matches a file, the most specific match wins: exact rules take priority, and literal
segments take priority over wildcards.

## Inherited permissions (2.0)

Package rules inherit permissions from their nearest enclosing tracked package by default.
A child adds its own permissions to the parent's effective allowlist:

```yaml
rules:
  dev.ionfusion.runtime:
    - dev.ionfusion.commons
    - dev.ionfusion.fusion
  dev.ionfusion.runtime.base:
    - dev.ionfusion.runtime._private.util
```

Here, `runtime.base` can import from `commons`, `fusion`, and `_private.util`.
Inheritance is recursive and independent of declaration order. It also applies to arrow rules
and packages tracked only because they appear as dependency targets. An empty list adds no
permissions; it still inherits the parent's permissions.

Use the object form to start a fresh allowlist:

```yaml
rules:
  dev.ionfusion.runtime:
    - dev.ionfusion.commons
    - dev.ionfusion.fusion
  dev.ionfusion.runtime.isolated:
    inherit: false
    allow:
      - dev.ionfusion.commons
```

`runtime.isolated` cannot import from `fusion`. Its children inherit this restricted allowlist;
they do not regain permissions from `runtime`. To grant no imports from other tracked layers,
use `inherit: false` with `allow: []`. Existing permissions within the same tracked layer and
additive `classAllowlist` exceptions still apply.

The object form requires `allow` and accepts an optional boolean `inherit`, which defaults to
`true`. It also works with composite keys. Unknown options and invalid values are errors.

Inheritance follows literal package boundaries. `com.app` and `com.app.**` are the same
rule: their allowlists merge, and an explicit `inherit` setting applies to the combined rule.
Only conflicting explicit settings are rejected. A list or an object without `inherit` leaves
that setting unspecified; it defaults to `true` after all entries have been combined.

An exact rule such as `com.app!` can inherit from `com.app`, but cannot be a parent itself.
A wildcard rule inherits from the nearest literal package enclosing the source file's actual
package. For example, `com.app.*.api` checking `com.app.foo.api` inherits `com.app.foo`'s
effective permissions, including any reset there. Its own grants are then added. Setting
`inherit: false` on the wildcard rule starts a fresh allowlist for every matching package.
Wildcard rules do not inherit from one another, and literal rules do not inherit from general
wildcards. Single-segment shorthand rules keep their existing behavior.

See [migration guidance](migration.md#inherited-permissions) before upgrading an existing configuration.

## Dependency targets

Dependency targets are matched against the imported package itself. Allowing
`com.example.app.domain` (or `com.example.app.domain.**`) grants access to its subpackages even
when those subpackages have their own rules. An exact target such as `com.example.app.domain!`
still grants access only to that package.

## Glob patterns

For fully-qualified package names, Structural supports Ant-style wildcards so you don't have to
spell out every subpackage by hand:

| Pattern | Matches |
| --- | --- |
| `com.example.foo` | `com.example.foo` and any subpackage (the default) |
| `com.example.foo.**` | same as above — explicit form, useful for readability |
| `com.example.foo!` | **only** `com.example.foo`, no subpackages |
| `com.example.*` | direct children of `com.example` only (`com.example.foo`, not `com.example.foo.bar`) |
| `com.*.api` | any `com.X.api` where `X` is a single segment |
| `com.**.internal` | any package under `com.` that ends in `.internal`, plus `com.internal` itself |
| `**.private` | any package ending in `.private`, plus `private` itself |

A few things worth knowing:

- `*` matches exactly one package segment.
- `**` matches zero or more.
- A trailing `!` pins the rule to that exact package and can't be combined with wildcards.
- Wildcards and `!` only work on dotted tokens — they're rejected on single-segment names
  like `data`.
- Each side of an arrow rule is parsed on its own, so `com.example.api! -> com.example.impl.**` is valid.

Here's the example that prompted this feature: letting everything under `dev.ionfusion.runtime`
import from anything under `dev.ionfusion.runtime._private`.

```yaml
rules:
  - dev.ionfusion.runtime._private -> dev.ionfusion.runtime
```

Or, if you want to lock both sides down to the exact packages and ignore subpackages entirely:

```yaml
rules:
  - dev.ionfusion.runtime._private! -> dev.ionfusion.runtime!
```
