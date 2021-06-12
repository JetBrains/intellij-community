fun test(s: String, i: Int) = Unit

fun usages() {
    test("test", 42)
    test(s = "test", i = 42)
}
