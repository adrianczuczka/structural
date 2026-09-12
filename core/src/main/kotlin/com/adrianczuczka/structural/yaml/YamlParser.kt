package com.adrianczuczka.structural.yaml

import org.gradle.api.GradleException
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * YAML file format should be like this example:
 *
 * Arrow form:
 * rules:
 *   - data <- domain -> ui
 *   - local <- data
 *   - remote <- data
 *   - legacy                  # bare identifier: tracked, no allowed imports
 *
 * Or map form (key is the importer):
 * rules:
 *   domain:
 *     - ui
 *     - data
 *   data:
 *     - local
 *     - remote
 *   legacy: []                # tracked, no allowed imports
 *
 * Tracked packages are inferred from the identifiers used in `rules:`. Any
 * package that appears on either side of an arrow rule, or as a key/value in
 * the map form, is tracked. A bare identifier (no arrow) registers a package
 * as tracked with no allowed imports. Imports from packages outside the
 * tracked set are unconditionally allowed.
 *
 * Package tokens accept Ant-style globs on multi-segment (fully-qualified)
 * paths. See [parseTrackedPackage] for the supported grammar:
 *   - bare `com.example` matches the path and any subpackage
 *   - `com.example.**` is the explicit form of the above
 *   - `com.example!` is an exact match (no subpackages)
 *   - `com.*.api`, `com.**.internal` match with single- or multi-segment wildcards
 *
 * Single-segment tokens (like `data`) keep the legacy last-segment matching
 * and cannot carry wildcards or `!`.
 *
 * An optional top-level `classAllowlist:` section grants fine-grained
 * class-level permissions on top of the package-level rules. Class rules are
 * purely additive: they permit a cross-package import that package rules
 * would otherwise reject. They never deny what package rules allow.
 *
 * classAllowlist:
 *   - "com.example.api.** <- com.example.impl.FusionException"
 *   - "com.example.api.ApiBuilder <- com.example.impl.**"
 *
 * Or in map form (key is the importer):
 *
 * classAllowlist:
 *   "com.example.api.**":
 *     - com.example.impl.FusionException
 *     - com.example.impl._Private_*
 *
 * Every package referenced by a class rule must be tracked in `rules:`. If
 * the importer's or imported's package portion isn't covered by any tracked
 * package, the rule is rejected at parse time — at runtime it would do
 * nothing (the file or import wouldn't be checked at all), so the silent
 * no-op is converted into an actionable error.
 *
 * See [parseClassRuleToken] for the disambiguation rule between package and
 * class portions of a token.
 */
fun File.parseYamlImportRules(): StructuralData? =
    if (exists()) {
        val data: Map<*, *> = Yaml().load(inputStream())
        if (data.containsKey("packages")) {
            throw GradleException(
                "The `packages:` block has been removed. Tracked packages are now inferred from " +
                    "`rules:`. Delete the `packages:` block; for any entry that doesn't already " +
                    "appear in a rule, add it as a bare list item under `rules:` (e.g. `- legacy`) " +
                    "or in map form with an empty value (`legacy: []`)."
            )
        }
        if (data.containsKey("classes")) {
            throw GradleException(
                "The `classes:` block has been renamed to `classAllowlist:` to make its purpose " +
                    "explicit — it's an allowlist of class-level imports, not a constraint. " +
                    "Rename `classes:` to `classAllowlist:` in your config."
            )
        }
        val supportedKeys = setOf("rules", "classAllowlist")
        data.keys.forEach { key ->
            if (key !in supportedKeys) {
                val suggestion = supportedKeys.firstOrNull {
                    key is String && it.equals(key, ignoreCase = true)
                }
                val hint = if (suggestion != null) {
                    "Did you mean `$suggestion`?"
                } else {
                    val supportedKeysDescription = supportedKeys.joinToString(" and ") { "`$it`" }
                    "Supported keys are $supportedKeysDescription."
                }
                throw GradleException("Unknown configuration key `$key`. $hint")
            }
        }
        val allowedListPerPackage = mutableMapOf<TrackedPackage, MutableList<TrackedPackage>>()
        val rawRules = data["rules"]
        val rawClassRules = data["classAllowlist"]

        if (rawRules == null && rawClassRules == null) {
            throw GradleException("No rules or classAllowlist specified in config file")
        }

        when (rawRules) {
            null -> Unit
            is List<*> -> {
                rawRules.forEach { rule ->
                    if (rule is String) {
                        val regex = """(<-|->)""".toRegex()

                        val parts = regex.split(rule).map { it.trim() }
                        val arrows = regex.findAll(rule).map { it.value }.toList()

                        if (parts.any { it.isBlank() }) {
                            throw GradleException(
                                "Invalid rule format: '$rule'. Each side of an arrow must be a non-empty package token."
                            )
                        }

                        val parsedParts = parts.map { parseTrackedPackage(it) }
                        parsedParts.forEach {
                            allowedListPerPackage.computeIfAbsent(it) { mutableListOf() }
                        }

                        arrows.forEachIndexed { index, arrow ->
                            val source = parsedParts[index]
                            val target = parsedParts[index + 1]
                            val key = if (arrow == "->") target else source
                            val value = if (arrow == "->") source else target
                            allowedListPerPackage.computeIfAbsent(key) { mutableListOf() } += value
                        }
                    }
                }
            }
            is Map<*, *> -> {
                rawRules.forEach { (key, value) ->
                    if (key is String && value is List<*>) {
                        allowedListPerPackage.addAllowedPackageToKeyIfPossible(key, value)
                    } else if (key is List<*> && value is List<*>) {
                        key
                            .filterNotNull()
                            .forEach { test ->
                                allowedListPerPackage.addAllowedPackageToKeyIfPossible(test, value)
                            }
                    }
                }
            }
            else -> throw GradleException("Invalid rules format in config file. Rules must be a list of arrow rules or a map of package dependencies.")
        }

        if (allowedListPerPackage.isEmpty()) {
            throw GradleException(
                "No tracked packages found. The `rules:` block must declare at least one package " +
                    "(via an arrow rule, a map entry, or a bare list item like `- legacy`)."
            )
        }

        val classRules = parseClassRulesSection(rawClassRules)
        val validation = validateClassRules(classRules, allowedListPerPackage)

        StructuralData(
            allowedListPerPackage.keys.toList(),
            allowedListPerPackage,
            classRules,
            validation.warnings,
        )
    } else {
        null
    }

private fun parseClassRulesSection(raw: Any?): List<ClassRule> {
    if (raw == null) return emptyList()
    val rules = mutableListOf<ClassRule>()
    when (raw) {
        is List<*> -> {
            raw.filterIsInstance<String>().forEach { ruleStr ->
                val regex = """(<-|->)""".toRegex()
                val parts = regex.split(ruleStr).map { it.trim() }
                val arrows = regex.findAll(ruleStr).map { it.value }.toList()

                if (arrows.isEmpty() || parts.any { it.isBlank() }) {
                    throw GradleException(
                        "Invalid class rule format: '$ruleStr'. Class rules must contain <- or -> arrows."
                    )
                }

                val parsedParts = parts.map { parseClassRuleToken(it) }
                arrows.forEachIndexed { index, arrow ->
                    val left = parsedParts[index]
                    val right = parsedParts[index + 1]
                    val (importer, imported) = if (arrow == "->") {
                        right to left
                    } else {
                        left to right
                    }
                    rules += ClassRule(importer, imported)
                }
            }
        }
        is Map<*, *> -> {
            raw.forEach { (key, value) ->
                if (key is String && value is List<*>) {
                    val importer = parseClassRuleToken(key)
                    value.filterIsInstance<String>().forEach { v ->
                        rules += ClassRule(importer, parseClassRuleToken(v))
                    }
                }
            }
        }
        else -> throw GradleException(
            "Invalid classAllowlist format in config file. classAllowlist must be a list of arrow rules or a map of class dependencies."
        )
    }
    return rules.distinct()
}

private fun MutableMap<TrackedPackage, MutableList<TrackedPackage>>.addAllowedPackageToKeyIfPossible(
    key: Any,
    value: Any,
) {
    if (key is String && value is List<*>) {
        val parsedKey = parseTrackedPackage(key)
        val parsedValues = value
            .filterIsInstance<String>()
            .map { parseTrackedPackage(it) }
        this[parsedKey] =
            (getOrDefault(parsedKey, emptyList()) + parsedValues)
                .distinct()
                .toMutableList()
        parsedValues.forEach { computeIfAbsent(it) { mutableListOf() } }
    }
}

data class StructuralData internal constructor(
    internal val checkedPackages: List<TrackedPackage>,
    internal val rules: Map<TrackedPackage, List<TrackedPackage>>,
    internal val classRules: List<ClassRule> = emptyList(),
    internal val warnings: List<String> = emptyList(),
)
