package com.adrianczuczka.structural.yaml

/**
 * Resolve permissions once, before checking imports. Only literal subtrees
 * (bare paths or paths ending in .**) can be parents. An exact rule can
 * inherit from the subtree at the same path, but cannot itself be a parent.
 * General globs inherit from their literal prefix's ancestors; overlapping
 * globs are independent. Single-segment rules retain their existing behavior.
 */
internal fun resolvePackageInheritance(
    rules: Map<TrackedPackage, List<TrackedPackage>>,
    inheritance: Map<TrackedPackage, Boolean>,
): Map<TrackedPackage, List<TrackedPackage>> {
    val resolved = mutableMapOf<TrackedPackage, List<TrackedPackage>>()
    val subtrees = rules.keys.mapNotNull { rule ->
        val path = rule.pattern.removeSuffix(".**")
        if (rule.isSingleSegment || '*' in path || '!' in path) null else rule to path
    }

    fun resolve(rule: TrackedPackage): List<TrackedPackage> {
        resolved[rule]?.let { return it }
        val own = rules.getValue(rule)
        val effective = if (rule.isSingleSegment || inheritance[rule] == false) own else {
            val prefix = rule.pattern.removeSuffix("!").split('.').takeWhile { '*' !in it }.joinToString(".")
            val candidates = subtrees.filter { (parent, path) ->
                parent != rule && (prefix.startsWith("$path.") ||
                    (prefix == path && (rule.pattern.endsWith('!') ||
                        ('*' in rule.pattern && rule.pattern != "$path.**"))))
            }
            val depth = candidates.maxOfOrNull { (_, path) -> path.length }
            val inherited = candidates.filter { (_, path) -> path.length == depth }
                .flatMap { (parent, _) -> resolve(parent) }
            inherited + own
        }
        return effective.distinct().also { resolved[rule] = it }
    }

    return rules.keys.associateWith { resolve(it) }
}
