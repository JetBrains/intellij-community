open class Base {
    protected fun protecte<caret>dFunction() = "bar"
}
val result = Base().protectedFunction()

// IGNORE_K1