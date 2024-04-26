// "Change getter type to HashSet<Int>" "true"

class A() {
    val i: java.util.HashSet<Int>
        get(): <caret>Any = java.util.LinkedHashSet<Int>()
}
/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFix