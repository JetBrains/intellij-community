package test

class X {
    val <caret>y = ""
    private val z = object : JavaInterface {
        override fun getX(): String {
            return y
        }
    }
}