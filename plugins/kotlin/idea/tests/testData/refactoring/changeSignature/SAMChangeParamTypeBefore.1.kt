fun test() {
    JTest.samTest(SAM { s, n -> s + " " })
    JTest.samTest(SAM { s: String, n: Int -> x + " " })
}