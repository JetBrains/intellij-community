open class <caret>Base {
    // INFO: {"checked": "true"}
    companion object {
        fun obj() {}
    }
}

object Impl : Base()
