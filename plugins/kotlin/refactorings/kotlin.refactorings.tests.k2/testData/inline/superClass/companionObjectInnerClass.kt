open class <caret>Base {
    companion object {
        const val ANSWER = 42

        fun answer() = ANSWER
    }

    inner class Reader {
        fun read() = answer()
        fun owner() = this@Base
    }

    fun reader() = Reader()
}

class Child : Base()

fun read(child: Child) = child.reader().read() + Base.answer()
