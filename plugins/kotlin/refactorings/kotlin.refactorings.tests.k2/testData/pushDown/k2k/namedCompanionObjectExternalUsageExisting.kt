open class <caret>Base {
    // INFO: {"checked": "true"}
    companion object Factory {
        fun create() = Base()
    }
}

class Child : Base() {
    companion object {
        fun existing() {}
    }
}

fun usage() = Base.Factory.create()
