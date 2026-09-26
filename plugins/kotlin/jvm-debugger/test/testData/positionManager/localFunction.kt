package test

fun foo(): String {
    fun bar(): String {
        return ""   // test.LocalFunctionKt
    }
    return bar()
}
