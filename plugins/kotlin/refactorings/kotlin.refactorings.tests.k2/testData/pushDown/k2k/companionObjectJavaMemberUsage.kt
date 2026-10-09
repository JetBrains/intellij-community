package sample

open class <caret>Base {
    // INFO: {"checked": "true"}
    companion object {
        @JvmStatic
        fun obj() {}
    }
}

class Child : Base()
