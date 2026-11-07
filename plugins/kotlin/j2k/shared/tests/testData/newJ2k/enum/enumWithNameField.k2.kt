// ERROR: 'name' hides member of supertype 'Enum' and needs an 'override' modifier.
internal enum class E {
    I;

    private val name: String? = null
}
