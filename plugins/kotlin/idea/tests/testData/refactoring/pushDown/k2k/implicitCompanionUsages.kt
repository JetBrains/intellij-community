class X {
    open class <caret>A {
        // INFO: {"checked": "true"}
        fun foo(): Int = bar() + A.bar() + X.A.bar() + BAZ + A.BAZ + X.A.BAZ

        companion object {
            fun bar() = 1
            const val BAZ: Int = 0
        }
    }
}

class B : X.A