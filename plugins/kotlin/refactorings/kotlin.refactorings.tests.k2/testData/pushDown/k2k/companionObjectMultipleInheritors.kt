open class <caret>Base {
    // INFO: {"checked": "true"}
    companion object {
        fun obj() {}
    }
}

class First : Base()

class Second : Base()

fun usage() {
    Base.obj()
}
