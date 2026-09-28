# Sources and compatibility

[← README](../README.md)

## Which sources are checked

When a JVM plugin (`java`, `kotlin("jvm")`, …) is applied, Structural checks the `main` source
set – including any custom `srcDir` you've registered. Without one, it falls back to scanning the
project directory for `**/src/main/kotlin` and `**/src/main/java` layouts; that scan skips the
build directory and any nested checkout (a directory containing a `.git` entry, such as a git
worktree added inside the project). To take full control, set `source` explicitly – it overrides
both:

```kts
structural {
    source.from("src/main/kotlin", "src/generated")
}
```

`structuralCheck` is incremental and cacheable: it only reruns when the sources, rules, or
baseline change, and its result is shared through the build cache – including across git worktrees
or other checkouts of the same repository.

## Compatibility

Structural works on Kotlin and Java sources. The Kotlin parser runs in an isolated classloader,
which means the plugin uses its own bundled Kotlin compiler — so it doesn't matter what Kotlin
version your project uses (or whether you use Kotlin at all).
