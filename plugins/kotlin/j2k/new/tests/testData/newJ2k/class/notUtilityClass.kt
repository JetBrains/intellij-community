internal open class Base {
    companion object {
        const val CONSTANT: Int = 10
    }
}

internal class Derived : Base() {
    fun foo() {}
}
