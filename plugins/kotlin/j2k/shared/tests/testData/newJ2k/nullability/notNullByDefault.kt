import org.jetbrains.annotations.NotNullByDefault

@NotNullByDefault
internal interface Test {
    fun str(): String

    fun nullableStr(): String?
}
