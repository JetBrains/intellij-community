open class <caret>Base {
    companion object {
        // INFO: {"checked": "true"}
        fun obj() {}
        val keep = 1
    }
}

class First : Base() {
    companion object {
        fun existing() {}
    }
}

class Second : Base()
