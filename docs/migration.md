# Migrating from 1.x

[← README](../README.md)

- `config` and `baseline` are file properties now, not strings: replace
  `config = "$rootDir/structural.yml"` with `config.set(file("$rootDir/structural.yml"))`.
- `structuralAggregateBaseline` is no longer registered automatically – apply
  `com.adrianczuczka.structural.aggregation` to the root project to get it.
- Projects applying a JVM plugin are checked by source set now rather than by directory layout.
  If you relied on the old directory scan (e.g. sources outside any source set), set
  `structural { source.from(...) }` explicitly.
