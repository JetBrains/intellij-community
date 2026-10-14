fun record(value: Int) = value

open class <caret>Base(first: Int, second: Int) {
    val result = first - second
}

class Child : Base(second = record(2), first = record(1)) {
    val last = record(3)
}
