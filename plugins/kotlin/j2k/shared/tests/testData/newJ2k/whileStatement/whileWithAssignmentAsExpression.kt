class SomeClass {
    var a: Int = 0
    var b: Int = 0
    fun doSomeWhile(i: Int) {
        while (i < 0) {
            b = i
            a = b
        }
    }
}
