// FLOW: IN

interface Base {
    fun calc() : String
}

class Delegate : Base by Impl()

class Impl : Base {
    override fun calc(): String {
        return "impl"
    }
}

fun main(b: Delegate) {
    val <caret>s = b.calc()
}