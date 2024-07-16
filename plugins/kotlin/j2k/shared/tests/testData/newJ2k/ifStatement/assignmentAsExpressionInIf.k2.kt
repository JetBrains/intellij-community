class SomeClass {
    fun doSomeIf(i: Int) {
        var a: Int
        var b: Int
        var c: Int
        if (i < 0) {
            b = i
            a = b
        } else {
            c = i
            b = c
        }
    }
}
