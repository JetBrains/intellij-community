open class <caret>Base {
    inner class Reader {
        fun owner() = this@Base
    }

    fun reader() = Reader()
}

class Child : Base()
class Other : Base()
