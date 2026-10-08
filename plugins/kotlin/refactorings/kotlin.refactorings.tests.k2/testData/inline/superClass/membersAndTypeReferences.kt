open class <caret>Base {
    fun answer() = 42
}
class Child : Base()
fun use(value: Base) = value.answer()
