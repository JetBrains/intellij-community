object ConvertMe3 {
    fun foo(a: String?, b: Int) {}
    @JvmStatic
    fun foo(a: Int, b: String?) {}
}