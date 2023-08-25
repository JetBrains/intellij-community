// "Change getter type to Int" "true"
class A() {
    val i: Int
        get(): <caret>Any = 1
}
/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFix