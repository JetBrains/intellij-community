class SomeClass {
    var a: Int = 0
    var b: Int = 0
    fun doSomeWhile(i: Int) {
        do {
            b = i
            a = b
        } while (i < 0)
    }
}
