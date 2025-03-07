package com.adrianczuczka.structural.baseline

data class BaselineData(
    val violationsToIgnore: List<String>
)

fun BaselineData.toXml() =
    """
<?xml version="1.0" ?>
<StructuralBaseline>
  <CurrentIssues>
${violationsToIgnore.joinToString(separator = "\n") { violation -> "    <ID>$violation</ID>" }}
  </CurrentIssues>
</StructuralBaseline>
    """.trimIndent()