open class <caret>Base {
    companion object {
        const val BASE = 40

        fun baseAnswer() = BASE
    }
}

class Child : Base() {
    companion object {
        const val EXTRA = 2

        fun answer() = baseAnswer() + EXTRA
    }
}

fun baseAnswer() = Base.baseAnswer()
fun answer() = Child.answer()
