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