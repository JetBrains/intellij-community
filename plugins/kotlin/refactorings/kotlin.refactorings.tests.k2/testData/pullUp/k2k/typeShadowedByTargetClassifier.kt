open class Base {
    class Shared
}

class <caret>Child : Base() {
    class Shared

    // INFO: {"checked": "true"}
    fun use(value: Shared): Shared = value
}
