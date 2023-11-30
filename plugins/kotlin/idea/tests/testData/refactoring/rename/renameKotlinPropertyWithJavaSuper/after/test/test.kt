package test

class X {
    val x = ""
    private val z = object : JavaInterface {
        override fun getX(): String {
            return this@X.x
        }
    }
}