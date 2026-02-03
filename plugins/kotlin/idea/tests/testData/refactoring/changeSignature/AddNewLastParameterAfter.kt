fun test(s: String, i: Int) = Unit

fun usages() {
    test("test", 8, 9) //broken code
    test("test", 42)
    test(s = "test", i = 42)
}