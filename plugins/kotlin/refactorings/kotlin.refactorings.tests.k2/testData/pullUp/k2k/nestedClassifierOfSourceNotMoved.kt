open class Base

class <caret>Child : Base() {
    class Nested

    // INFO: {"checked": "true"}
    fun use(value: Nested): Nested = value
}
