class Character {
    val greeter = Greeter()

    fun hello() {
       greeter.helloo()
    }

    class Greeter {
        fun helloo() {
            println("helloo")
        }
    }
}


object Context

fun main() {
    with(Character()) {
        with(Context) {
            hel<caret>lo()
        }
    }
}
