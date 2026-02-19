// FLOW: IN

enum class ABC(<caret>x: Int) {
    A(1),
    B("2");
    constructor(s: String): this(s.length)
}