open class <caret>Base {
    // INFO: {"checked": "true"}
    companion object {
        fun obj() {}
    }
}

class Child : Base()

fun usage() {
    Base.obj()
    Base.Companion.obj()
}
