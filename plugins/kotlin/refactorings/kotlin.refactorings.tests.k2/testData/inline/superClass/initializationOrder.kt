fun record(value: Int) = value
open class <caret>Base {
    val first = record(1)
    init { record(2) }
    val second = record(3)
}
class Child : Base() {
    val third = record(4)
    init { record(5) }
}
