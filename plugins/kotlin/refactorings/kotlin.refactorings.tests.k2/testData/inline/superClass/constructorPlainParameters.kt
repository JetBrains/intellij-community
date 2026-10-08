open class <caret>Base(x: Int, text: String) {
    val doubled = x * 2

    init {
        println(text)
    }
}

class Child : Base(3, "base") {
    val result = doubled + 1
}
