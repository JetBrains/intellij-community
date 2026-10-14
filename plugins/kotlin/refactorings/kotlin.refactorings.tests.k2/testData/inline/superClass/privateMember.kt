open class <caret>Base {
    private val secret = 42

    private fun double() = secret * 2

    fun reveal() = double()
}

class Child : Base()
