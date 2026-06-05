package sample

class Target {
    fun foo(src: Src) {
        println(src.secret)
        println(this)
    }
}

class Src {
    private val secret: Int = 42

}

