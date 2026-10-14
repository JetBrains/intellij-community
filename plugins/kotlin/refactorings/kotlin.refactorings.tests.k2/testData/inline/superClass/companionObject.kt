open class <caret>Base {
    companion object {
        const val STEP = 2
        var count = 0

        fun next(): Int {
            count += STEP
            return count
        }
    }

    fun answer() = next()
}

class Child : Base()

fun next() = Base.next()
fun explicit() = Base.Companion.next()
fun reference() = Base.Companion::next
