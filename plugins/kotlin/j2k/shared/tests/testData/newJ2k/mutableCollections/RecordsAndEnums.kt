// ERROR: JVM_RECORDS_ILLEGAL_BYTECODE_TARGET
@JvmRecord
internal data class Names(val values: MutableList<String?>) {
    fun count(): Int {
        return values.size
    }
}

internal enum class Color(private val names: List<String>) {
    RED(listOf<String>("r"));

    fun count(): Int {
        return names.size
    }
}
