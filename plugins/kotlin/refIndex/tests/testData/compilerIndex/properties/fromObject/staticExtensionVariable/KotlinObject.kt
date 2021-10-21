package one.two

object KotlinObject {
    @JvmStatic
    var Int.staticExtension<caret>Variable
        get() = 42
        set(value) {}
}
