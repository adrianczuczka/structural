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

In your YAML file, there should be two main sections: `packages` and `rules`. `packages` should be a
list of all package names that Structural should check. For example, if you follow MVVM and Clean
Architecture rules, your list could look like this:

```yaml
packages:
  - local
  - remote
  - data
  - domain
  - ui
```

These are all the packages that will have rules related to which ones can import from which others.

The `rules` section should specify the rules which govern the package structure. There are two ways
to write these rules. You can either use arrows to specify the relationships, like this:

```yaml
rules:
  - data <- domain -> ui
  - local <- data
  - remote <- data
```

This means that

1. The `data` and `ui` folders can import from the `domain` folder, but not vice versa
2. The `local` and `remote` folders can import from the `data` folder, but not vice versa

The same rules can also be written like this:

```yaml
rules:

  # YAML lists are supported
  ? [ ui, data ]
    :
    - domain

  ? [ local, remote ]
    :
    - data

  # Also works
  ui:
    - domain
  data:
    - domain
  local:
    - data
  remote:
    - data
```

### Multi-segment package names

If your project uses packages that share common prefixes and can't be distinguished by a single
segment, you can use full dotted package names instead:

```yaml
packages:
  - "com.example.app.core"
  - "com.example.app.service"
  - "com.example.app.util"

rules:
  "com.example.app.core":
    - "com.example.app.service"
    - "com.example.app.util"
  "com.example.app.service":
    - "com.example.app.util"
```

This works the same way as single-segment names — files in `com.example.app.service` (and its
sub-packages) will be checked against `com.example.app.service`'s rules. When packages overlap,
the most specific (longest) match takes priority.

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