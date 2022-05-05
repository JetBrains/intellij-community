val example = "STRING_${substring<caret>()}_BIG"

private fun substring() = "SAMPLE"

fun extracted(): String = "42"