internal class X {
    var length: Int = 5
}

internal class C {
    fun foo(x: X) {
        for (i in 0 until x.length) {
            print(i)
        }
    }
}
