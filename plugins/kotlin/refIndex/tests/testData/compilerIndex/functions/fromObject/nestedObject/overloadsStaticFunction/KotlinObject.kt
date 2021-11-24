package one.two

object KotlinObject {
    object NestedObject {
        @JvmStatic
        @JvmOverloads
        fun overloadsStati<caret>cFunction(i: Int = 42, b: Boolean = false) {

        }
    }
}