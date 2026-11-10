// ERROR: Using @JvmRecord is only allowed with -jvm-target 16 or later (or -jvm-target 15 with the -Xjvm-enable-preview flag enabled).
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
