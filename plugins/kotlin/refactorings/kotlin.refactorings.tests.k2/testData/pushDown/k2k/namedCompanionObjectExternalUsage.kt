open class <caret>Base {
    // INFO: {"checked": "true"}
    companion object Factory {
        fun obj() {}
    }
}

class Child : Base()

fun usage() {
    Base.obj()
    Base.Factory.obj()
}
