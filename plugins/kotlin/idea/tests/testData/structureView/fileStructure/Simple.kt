class Test(
    val str: String,
    private val number: Integer
) {
    private val some = 1
    protected fun foo() = some
    public fun other() {}
}

// EXTRA_INFO_KEYS: visibility, icon