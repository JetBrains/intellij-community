class Test(
    val str: String,
    private val number: Integer
) {
    private val some = 1
    protected fun foo() = some
    public fun other() {}
}

internal class Simple

internal fun foo() = "bar"

// EXTRA_INFO_KEYS: visibility, icon