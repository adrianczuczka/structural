# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Structural is a Gradle plugin (`com.adrianczuczka.structural`) that enforces package-level import rules in Kotlin and Java projects. It parses source files to detect forbidden cross-package imports based on YAML-defined rules.

## Build & Test Commands

```bash
# Build everything
./gradlew build

# Run all plugin tests
./gradlew :core:test

# Run a single test by name
./gradlew :core:test --tests "com.adrianczuczka.structural.StructuralPluginTest.structuralCheck should fail when an invalid import is found"

# Run the plugin check on the example apps
./gradlew :kotlin-test-app:structuralCheck
./gradlew :java-test-app:structuralCheck
```

## Architecture

The plugin lives in `core/`. The `kotlin-test-app/` and `java-test-app/` modules are example projects that apply the plugin (they contain intentional violations for testing).

**Plugin flow:** `StructuralPlugin` registers Gradle tasks → `StructuralTask` submits work via Gradle's Worker API with classloader isolation → `StructuralWorkAction` performs the actual analysis.

**File parsing:** Kotlin files are parsed using `KtPsiFactory` from `kotlin-compiler-embeddable` (via `PsiFactoryProvider` singleton). Java files are parsed with regex. Both paths produce a `ParsedSourceFile` (defined in `Util.kt`).

**Package checking algorithm** (in `StructuralWorkAction`): For each source file, every segment of its package name is checked against the tracked packages list. If a segment matches, that layer's rules are enforced against all imports. This means a file in `com.example.data.local` is checked against `data`'s rules. Imports within the same tracked package hierarchy (e.g., `data.local` → `data.remote`) are allowed.

**YAML config** (`structural.yml`): Arrow notation defines allowed imports. `A <- B` means A can import from B. `A -> B` means B can import from A. Only the last segment of package names is used (e.g., `data`, not `com.example.data`).

**Baseline** (`baseline.xml`): Generated via `structuralGenerateBaseline`, stores current violations as XML so they can be ignored during `structuralCheck`. Violation IDs encode type, filename, line number, and package info.

## Key Technical Details

- Kotlin 2.1.0, Gradle 8.13, JDK 17
- Tests use Gradle TestKit (`GradleRunner`) to create temporary projects and run the plugin tasks
- The kotlin-compiler-embeddable runs in an isolated classloader to avoid conflicts with the host project
- Published to Maven Central via `io.deepmedia.tools.deployer`