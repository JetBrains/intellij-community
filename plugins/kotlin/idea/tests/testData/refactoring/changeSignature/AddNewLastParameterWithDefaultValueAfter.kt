fun test(s: String, i: Int = 42) = Unit

fun usages() {
    test("test")
    test(s = "test")
}