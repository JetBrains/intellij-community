import A.Nested

internal class A @JvmOverloads constructor(nested: Nested? = Nested(Nested.Companion.FIELD)) {
    internal class Nested(p: Int) {
        companion object {
            const val FIELD: Int = 0
        }
    }
}

internal class B {
    var nested: Nested? = null
}
