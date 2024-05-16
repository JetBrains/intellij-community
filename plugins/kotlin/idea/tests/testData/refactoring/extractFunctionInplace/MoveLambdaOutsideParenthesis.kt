fun interface FunctionalInterface {
    fun paramOutput(name: String): String
}

fun getFunInterface(): FunctionalInterface = FunctionalInterface <selection>{ paramName -> "ParamName is $paramName!" }</selection>
