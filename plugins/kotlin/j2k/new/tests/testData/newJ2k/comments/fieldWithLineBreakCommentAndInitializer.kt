package some

object Foo {
    var SOME // some comment
            : Int = 1

    fun test(n: Int) {
        val otp = n % SOME
    }
}
