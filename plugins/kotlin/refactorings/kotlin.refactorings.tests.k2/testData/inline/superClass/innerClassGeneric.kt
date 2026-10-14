open class <caret>Base<T> {
    fun value(value: T): T = value

    inner class Reader<U>(val extra: U) {
        fun read(value: T): Pair<T, U> = this@Base.value(value) to extra
    }
}

class Child : Base<String>()
