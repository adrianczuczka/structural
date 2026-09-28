package com.adrianczuczka.structural.yaml

/** Resolve literal rules once and wildcard permissions once per concrete source package. */
internal class PackagePermissions(
    private val rules: Map<TrackedPackage, List<TrackedPackage>>,
    private val inheritance: Map<TrackedPackage, Boolean>,
) {
    private val subtrees = rules.keys.filter {
        !it.isSingleSegment && '*' !in it.pattern && '!' !in it.pattern
    }.associateBy { it.pattern }
    private val resolved = mutableMapOf<TrackedPackage, List<TrackedPackage>>()
    private val byPackage = mutableMapOf<Pair<TrackedPackage, String>, List<TrackedPackage>>()

    // Wildcards have no single effective allowlist. Only their own grants are
    // guaranteed across all matching packages, so warning analysis uses those.
    val guaranteedRules: Map<TrackedPackage, List<TrackedPackage>> = rules.keys.associateWith {
        if ('*' in it.pattern) rules.getValue(it) else resolveLiteral(it)
    }

    fun forPackage(rule: TrackedPackage, packageName: String): List<TrackedPackage> {
        if ('*' !in rule.pattern) return guaranteedRules.getValue(rule)
        return byPackage.getOrPut(rule to packageName) {
            val parent = if (inheritance[rule] == false) null else nearestParent(packageName)
            (parent?.let { resolveLiteral(it) }.orEmpty() + rules.getValue(rule)).distinct()
        }
    }

    private fun resolveLiteral(rule: TrackedPackage): List<TrackedPackage> {
        resolved[rule]?.let { return it }
        val parent = when {
            rule.isSingleSegment || inheritance[rule] == false -> null
            rule.pattern.endsWith('!') -> nearestParent(rule.pattern.removeSuffix("!"))
            else -> nearestParent(rule.pattern.substringBeforeLast('.', ""))
        }
        return (parent?.let { resolveLiteral(it) }.orEmpty() + rules.getValue(rule))
            .distinct().also { resolved[rule] = it }
    }

    private fun nearestParent(packageName: String): TrackedPackage? {
        var path = packageName
        while (path.isNotEmpty()) {
            subtrees[path]?.let { return it }
            path = path.substringBeforeLast('.', "")
        }
        return null
    }
}

/** Canonicalize only literal recursive paths; com.** must not become shorthand com. */
internal fun parseRulePackage(raw: String): TrackedPackage {
    val rule = parseTrackedPackage(raw)
    val path = rule.pattern.removeSuffix(".**")
    return if (path != rule.pattern && '.' in path && '*' !in path) TrackedPackage(path) else rule
}
