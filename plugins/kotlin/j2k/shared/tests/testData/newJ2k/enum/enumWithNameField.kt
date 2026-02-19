// ERROR: 'name' in 'Enum' is final and cannot be overridden
// ERROR: Type of 'name' is not a subtype of the overridden property 'public final val name: String defined in kotlin.Enum'
internal enum class E {
    I;

    override val name: String? = null
}
