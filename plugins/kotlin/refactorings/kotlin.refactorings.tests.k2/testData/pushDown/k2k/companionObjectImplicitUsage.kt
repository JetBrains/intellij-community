open class <caret>Base {
    // INFO: {"checked": "true"}
    companion object {
        fun obj() = value()

        private fun value() = 1
    }
}

class Child : Base()

fun usage() = Base.obj()
