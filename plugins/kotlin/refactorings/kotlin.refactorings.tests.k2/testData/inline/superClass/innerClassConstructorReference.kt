open class <caret>Base {
    inner class Reader(val answer: Int)
}

class Child : Base()

fun reader(child: Child): Base.Reader {
    val create = child::Reader
    return create(42)
}
