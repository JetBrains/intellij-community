internal open class Base(nested: Nested?) {
    internal class Nested(p: Int) {
        companion object {
            const val FIELD: Int = 0
        }
    }
}

internal class Derived : Base(Nested(Nested.Companion.FIELD))
