open class <caret>Base {
    val value = 40
    fun helper() = 2
}

class Child : Base() {
    fun use() = super.value + super.helper()
}
