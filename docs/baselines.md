# Baselines

[← README](../README.md)

Adopting Structural on an existing codebase usually means a long initial list of violations.
Rather than fixing them all up front, you can snapshot the current state as a baseline and only
fail on *new* violations:

```bash
./gradlew structuralGenerateBaseline
```

That writes a baseline file (default: `baseline.xml` in the project directory) listing the
existing issues, which `structuralCheck` will then ignore. Point at a different location with:

```kts
structural {
    baseline.set(file("baseline.xml"))
}
```

Check the baseline into version control so the rest of your team gets the same behavior.

## Multi-module builds

In a multi-module build, `structuralGenerateBaseline` is registered per module — each module
writes its own baseline at the path it configured. That's the convention you'll recognise from
detekt and ktlint, and it's the default if you don't think about it.

If you want a *single* shared baseline across modules instead — every module pointing at one
`$rootDir/baseline.xml`, say — apply the aggregation plugin to your root project:

```kts
plugins {
    id("com.adrianczuczka.structural.aggregation") version "<latest>"
}
```

Its `structuralAggregateBaseline` task collects findings from every module that applies the
structural plugin (through dependency resolution, so it stays compatible with Gradle's Isolated
Projects mode) and writes one file per configured path with the aggregated, deduplicated entries.
Use it instead of `structuralGenerateBaseline` for shared-baseline workflows; using both at once
on a shared path will race.
