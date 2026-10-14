open class <caret>Base {
    // INFO: {"checked": "true"}
    companion object {
        fun obj() = 1
    }

    // INFO: {"checked": "true"}
    fun use() = obj() + Base.obj()
}

class Child : Base()
