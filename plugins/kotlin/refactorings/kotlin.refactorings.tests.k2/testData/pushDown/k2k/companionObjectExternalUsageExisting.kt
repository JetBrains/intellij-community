open class <caret>Base {
    // INFO: {"checked": "true"}
    companion object {
        fun obj() {}
    }
}

class Child : Base() {
    companion object {
        fun m() {}
    }
}

fun usage() {
    Base.obj()
    Base.Companion.obj()
}
