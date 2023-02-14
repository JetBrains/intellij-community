object ConvertMe1 {
    @JvmStatic
    fun foo(a: String?, b: String?) {}
    @JvmStatic
    fun foo(a: String?) {}
    @JvmStatic
    fun foo(vararg values: Int) {}
    fun foo() {}
}