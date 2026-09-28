# Migration

[← README](../README.md)

## Inherited permissions

For upgrades from 1.x or 2.0.0-beta1 through beta3 to 2.0.0-beta4 or later:

**This changes which imports an existing configuration permits.** A nested rule that previously
replaced a parent allowlist now extends it. Review nested rules before upgrading to 2.0.0-beta4 or later.
To preserve replacement behavior, convert each nested importer rule to the object form with
`inherit: false` and put its existing dependency list under `allow`. Convert arrow rules to
map form when you need this override. Packages mentioned only as dependency targets may also
need an explicit entry with `inherit: false` and `allow: []` to preserve their old behavior.

For example, this configuration lets application wiring import every layer:

```yaml
rules:
  com.app: [com.app.data, com.app.domain, com.app.ui]
```

Starting with 2.0.0-beta4, each target also inherits `com.app`'s permissions. This permits sibling imports such
as `com.app.domain` importing `com.app.data`, which the same configuration previously rejected.
To keep the wiring permissions while preserving isolation between those layers, reset them:

```yaml
rules:
  com.app: [com.app.data, com.app.domain, com.app.ui]
  ? [com.app.data, com.app.domain, com.app.ui]
  :
    inherit: false
    allow: []
```

Add any intended inter-layer dependencies to those layers' own allowlists.

See [inherited permissions](configuration.md#inherited-permissions-20) for the full rules.

## Gradle configuration changes from 1.x

- `config` and `baseline` are file properties now, not strings: replace
  `config = "$rootDir/structural.yml"` with `config.set(file("$rootDir/structural.yml"))`.
- `structuralAggregateBaseline` is no longer registered automatically – apply
  `com.adrianczuczka.structural.aggregation` to the root project to get it.
- Projects applying a JVM plugin are checked by source set now rather than by directory layout.
  If you relied on the old directory scan (e.g. sources outside any source set), set
  `structural { source.from(...) }` explicitly.
