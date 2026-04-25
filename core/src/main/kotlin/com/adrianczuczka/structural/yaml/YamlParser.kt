package com.adrianczuczka.structural.yaml

import org.gradle.api.GradleException
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * YAML file format should be like this example:
 *
 * packages:
 *   - local
 *   - remote
 *   - data
 *   - domain
 *   - ui
 *
 * Either this
 * rules:
 *   - data <- domain -> ui
 *   - local <- data
 *   - remote <- data
 *
 * Or this
 * rules:
 *   domain:
 *     - ui
 *     - data
 *
 *   data:
 *     - local
 *     - remote
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
 * An optional top-level `classes:` section grants fine-grained class-level
 * permissions on top of the package-level rules. Class rules are purely
 * additive: they can permit a cross-package import that package rules would
 * otherwise reject. They never deny what package rules allow.
 *
 * classes:
 *   - "com.example.api.** <- com.example.impl.FusionException"
 *   - "com.example.api.ApiBuilder <- com.example.impl.**"
 *
 * Or in map form (key is the importer):
 *
 * classes:
 *   "com.example.api.**":
 *     - com.example.impl.FusionException
 *     - com.example.impl._Private_*
 *
 * See [parseClassRuleToken] for the disambiguation rule between package and
 * class portions of a token.
 */
fun File.parseYamlImportRules(): StructuralData? =
    if (exists()) {
        val data: Map<String, Any> = Yaml().load(inputStream())
        val allowedListPerPackage = mutableMapOf<TrackedPackage, MutableList<TrackedPackage>>()
        val rawCheckedPackages = (data["packages"] as? List<*>)?.filterIsInstance<String>()
        val rawRules = data["rules"]
        val rawClassRules = data["classes"]

        if (rawRules == null && rawClassRules == null) {
            throw GradleException("No rules or classes specified in config file")
        }
        if (rawCheckedPackages.isNullOrEmpty()) {
            throw GradleException("No packages specified to check in config file")
        }

        val checkedPackages = rawCheckedPackages.map { parseTrackedPackage(it) }
        checkedPackages.forEach {
            allowedListPerPackage.computeIfAbsent(it) { mutableListOf() }
        }
        when (rawRules) {
            null -> Unit
            is List<*> -> {
                rawRules.forEach { rule ->
                    if (rule is String) {
                        val regex = """(<-|->)""".toRegex()

                        val parts = regex.split(rule).map { it.trim() }
                        val arrows = regex.findAll(rule).map { it.value }.toList()

                        if (arrows.isEmpty() || parts.any { it.isBlank() }) {
                            throw GradleException("Invalid rule format: '$rule'. Rules must contain <- or -> arrows.")
                        }

                        val parsedParts = parts.map { parseTrackedPackage(it) }

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

        val classRules = parseClassRulesSection(rawClassRules)

        StructuralData(
            checkedPackages,
            allowedListPerPackage,
            classRules,
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
            "Invalid classes format in config file. Classes must be a list of arrow rules or a map of class dependencies."
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
    }
}

data class StructuralData internal constructor(
    internal val checkedPackages: List<TrackedPackage>,
    internal val rules: Map<TrackedPackage, List<TrackedPackage>>,
    internal val classRules: List<ClassRule> = emptyList(),
)
