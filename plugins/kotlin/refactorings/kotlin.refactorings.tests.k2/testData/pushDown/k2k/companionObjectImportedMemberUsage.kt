package sample

import sample.Base.Companion.obj

open class <caret>Base {
    // INFO: {"checked": "true"}
    companion object {
        fun obj() {}
    }
}

class Child : Base()

fun usage() {
    obj()
}
