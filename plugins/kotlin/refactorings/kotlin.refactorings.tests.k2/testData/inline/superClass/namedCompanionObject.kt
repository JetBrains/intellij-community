open class <caret>Base {
    companion object Factory {
        fun self(): Factory = this@Factory
    }
}

class Child : Base()

fun implicit() = Base
fun explicit(): Base.Factory = Base.Factory
fun same() = Base === Base.Factory
fun type() = Base.Factory::class
