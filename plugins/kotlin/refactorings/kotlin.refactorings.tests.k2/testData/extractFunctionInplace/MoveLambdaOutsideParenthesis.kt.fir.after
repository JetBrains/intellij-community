fun interface FunctionalInterface {
    fun paramOutput(name: String): String
}

fun getFunInterface(): FunctionalInterface = FunctionalInterface(function<caret>())

private fun function(): (String) -> String = { paramName -> "ParamName is $paramName!" }
