open class <caret>Base {
    // INFO: {"checked": "true"}
    companion object {
        fun clash() {}
        val number = 1
        class Nested
    }
}

class Child : Base() {
    companion object {
        fun clash() {}
        val number = 2
        class Nested
    }
}
