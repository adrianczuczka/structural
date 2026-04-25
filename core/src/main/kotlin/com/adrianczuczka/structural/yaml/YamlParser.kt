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
 */
fun File.parseYamlImportRules(): StructuralData? =
    if (exists()) {
        val data: Map<String, Any> = Yaml().load(inputStream())
        val allowedListPerPackage = mutableMapOf<TrackedPackage, MutableList<TrackedPackage>>()
        val rawCheckedPackages = (data["packages"] as? List<*>)?.filterIsInstance<String>()
        val rawRules = data["rules"] ?: throw GradleException("No rules specified in config file")

        if (rawCheckedPackages.isNullOrEmpty()) {
            throw GradleException("No packages specified to check in config file")
        }

        val checkedPackages = rawCheckedPackages.map { parseTrackedPackage(it) }
        checkedPackages.forEach {
            allowedListPerPackage.computeIfAbsent(it) { mutableListOf() }
        }
        when (rawRules) {
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

        StructuralData(
            checkedPackages,
            allowedListPerPackage
        )
    } else {
        null
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
)
