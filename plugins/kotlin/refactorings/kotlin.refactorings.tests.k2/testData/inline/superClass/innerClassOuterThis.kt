open class <caret>Base {
    private val answer = 42

    inner class Reader {
        private val answer = -1

        fun outerAnswer() = this@Base.answer
        fun innerAnswer() = this@Reader.answer
    }
}

class Child : Base()

fun reader(child: Child): Base.Reader = child.Reader()
