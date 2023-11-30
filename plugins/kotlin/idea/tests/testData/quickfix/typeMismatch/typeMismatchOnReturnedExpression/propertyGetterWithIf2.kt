// "Change type of 'foo' to 'Any'" "true"
class O
class P

val foo: O
    get() {
        return if (true) O() else P()<caret>
    }

/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix