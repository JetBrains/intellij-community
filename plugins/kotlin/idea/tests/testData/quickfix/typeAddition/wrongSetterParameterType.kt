// "Change setter parameter type to Int" "true"
class A() {
    var i: Int = 0
        set(v: <caret>Any) {}
}
/* IGNORE_FIR */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFix