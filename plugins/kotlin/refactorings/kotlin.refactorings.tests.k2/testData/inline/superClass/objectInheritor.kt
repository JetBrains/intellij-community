open class <caret>Base {
    fun answer() = 42
}

object Singleton : Base()

fun use() = Singleton.answer()
