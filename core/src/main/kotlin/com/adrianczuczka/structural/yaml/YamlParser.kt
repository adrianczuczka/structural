package com.adrianczuczka.structural.yaml

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
 */
fun File.parseYamlImportRules(): StructuralData? =
    if (exists()) {
        val data: Map<String, Any> = Yaml().load(inputStream())
        val allowedListPerPackage = mutableMapOf<String, MutableList<String>>()
        val checkedPackages = (data["packages"] as? List<*>)?.filterIsInstance<String>()
        val rawRules = data["rules"] ?: throw Exception("No rules specified in config file")

        if (checkedPackages.isNullOrEmpty()) {
            throw Exception("No packages specified to check in config file")
        }

        // Each checked package should be allowed to import from within itself
        checkedPackages.forEach {
            allowedListPerPackage.computeIfAbsent(it) { mutableListOf() } += it
        }
        when (rawRules) {
            is List<*> -> {
                rawRules.forEach { rule ->
                    if (rule is String) {
                        val regex = """(<-|->)""".toRegex()

                        val parts = regex.split(rule).map { it.trim() }
                        val arrows = regex.findAll(rule).map { it.value }.toList()

                        arrows.forEachIndexed { index, arrow ->
                            val source = parts[index]
                            val target = parts[index + 1]
                            val key =
                                if (arrow == "->") {
                                    target
                                } else {
                                    source
                                }
                            val value =
                                if (arrow == "->") {
                                    source
                                } else {
                                    target
                                }
                            allowedListPerPackage.computeIfAbsent(key) { mutableListOf() } += value
                        }
                    }
                }
            }
            is Map<*, *> -> {
                // Process list-based syntax
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
            else -> println("⚠️ Warning: Invalid format in import-rules.yml")
        }

        StructuralData(
            checkedPackages,
            allowedListPerPackage
        )
    } else {
        null
    }

private fun MutableMap<String, MutableList<String>>.addAllowedPackageToKeyIfPossible(
    key: Any,
    value: Any,
) {
    if (key is String && value is List<*>) {
        this[key] =
            (getOrDefault(key, emptyList()) + value.filterIsInstance<String>())
                .distinct()
                .toMutableList()
    }
}

data class StructuralData(
    val checkedPackages: List<String>,
    val rules: Map<String, List<String>>,
)