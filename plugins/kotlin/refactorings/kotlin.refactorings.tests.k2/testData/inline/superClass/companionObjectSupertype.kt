interface Factory {
    fun answer(): Int
}

open class <caret>Base {
    companion object : Factory {
        override fun answer() = 42
    }
}

class Child : Base()

fun factory(): Factory = Base
