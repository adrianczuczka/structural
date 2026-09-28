# Structural

[![Maven Central](https://img.shields.io/maven-central/v/com.adrianczuczka/structural)](https://central.sonatype.com/artifact/com.adrianczuczka/structural)
[![GitHub issues](https://img.shields.io/github/issues/adrianczuczka/structural)](https://github.com/adrianczuczka/structural/issues)
[![Build](https://img.shields.io/github/actions/workflow/status/adrianczuczka/structural/gradle.yml)](https://github.com/adrianczuczka/structural/actions)

Enforce architectural boundaries in Kotlin and Java with a simple YAML file. Catch forbidden
imports during your Gradle build, without splitting your project into modules.

Use it for Clean Architecture, MVVM, hexagonal architecture, or your own package conventions.

## Quick start

### 1. Apply the plugin

Add Maven Central to plugin resolution in `settings.gradle.kts` (merge this into your existing
`pluginManagement` block, if present):

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Apply Structural in the `build.gradle.kts` of the project you want to check. Replace `<latest>`
with the version shown in the Maven Central badge above:

```kotlin
plugins {
    id("com.adrianczuczka.structural") version "<latest>"
}

repositories {
    mavenCentral()
}
```

### 2. Define your boundaries

Create `structural.yml` next to that project's build file:

```yaml
rules:
  - data <- domain -> ui
```

This allows `data` and `ui` to import from `domain`. Imports in the reverse direction, or between
`data` and `ui`, fail the check.

### 3. Run the check

```bash
./gradlew structuralCheck
```

A forbidden import produces a message like this (path shortened):

```text
🚨 Import rule violations found:
/src/main/kotlin/com/example/ui/Screen.kt:3 : `com.example.ui` cannot import from `com.example.data`
```

## How rules work

**`data <- domain` means `data` may import from `domain`.** You can express the same rule as
`domain -> data`, or use a map with the importer as the key:

```yaml
rules:
  data:
    - domain
  ui:
    - domain
```

For either configuration above:

| Importing package | Imported package | Result |
| --- | --- | --- |
| `com.example.ui` | `com.example.domain` | Allowed |
| `com.example.data` | `com.example.domain` | Allowed |
| `com.example.domain` | `com.example.data` | Forbidden |
| `com.example.ui` | `com.example.data` | Forbidden |

Every package identifier in `rules:` becomes a tracked layer. Short names such as `data` match
that package segment and its subpackages. Imports within a layer are allowed; imports between
tracked layers need an explicit rule. Imports from untracked packages, including standard and
third-party libraries, are allowed.

See [configuration](docs/configuration.md) for fully qualified names, wildcards, and more rule
formats, or [class allowlists](docs/class-allowlist.md) for exceptions to package boundaries.

## Add it to your build

Make `structuralCheck` a dependency of Gradle's `check` task to run it alongside your tests:

```kotlin
tasks.named("check") {
    dependsOn("structuralCheck")
}
```

Violations fail the task, so CI can run `./gradlew check` to enforce the same boundaries.
Structural checks the `main` source set when a JVM plugin is applied, and supports custom source
locations. The check is incremental and cacheable. See [sources and compatibility](docs/sources-and-compatibility.md).

## Adopt it in an existing project

Snapshot existing violations so the build only fails on new ones:

```bash
./gradlew structuralGenerateBaseline
```

This writes `baseline.xml` in the project directory. Commit it so the whole team uses the same
baseline. See [baselines](docs/baselines.md) for custom paths and shared baselines across modules.

## Documentation

- [Configuration](docs/configuration.md) — rule formats, package matching, and wildcards
- [Class allowlist](docs/class-allowlist.md) — exceptions, token syntax, and limitations
- [Sources and compatibility](docs/sources-and-compatibility.md) — source selection and Kotlin isolation
- [Baselines](docs/baselines.md) — existing violations and multi-module aggregation
- [Migrating from 1.x](docs/migration.md) — configuration and task changes

## Examples

Explore the [Kotlin](kotlin-test-app/) and [Java](java-test-app/) sample projects and their
[shared rules](structural/structural.yml). Both contain intentional violations to demonstrate
what Structural catches.

[Apache 2.0 license](LICENSE)
