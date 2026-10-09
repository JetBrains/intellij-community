package sample

open class <caret>Base {
    // INFO: {"checked": "true"}
    companion object
}

class Child : Base()
