// "Change getter type to HashSet<Int>" "true"

class A() {
    val i: java.util.HashSet<Int>
        get(): <caret>Any = java.util.LinkedHashSet<Int>()
}
/* IGNORE_FIR */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFix