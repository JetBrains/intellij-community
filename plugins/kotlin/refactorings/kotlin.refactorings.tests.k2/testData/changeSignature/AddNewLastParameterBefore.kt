fun te<caret>st(s: String) = Unit

fun usages() {
    test("test", 8, 9) //broken code
    test("test")
    test(s = "test")
}