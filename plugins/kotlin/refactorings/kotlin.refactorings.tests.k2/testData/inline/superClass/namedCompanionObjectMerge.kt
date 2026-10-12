open class <caret>Base {
    companion object Factory {
        const val ANSWER = 42
    }
}

class Child : Base() {
    companion object Existing {
        fun answer() = Base.Factory.ANSWER
    }
}

fun answer() = Base.Factory.ANSWER
