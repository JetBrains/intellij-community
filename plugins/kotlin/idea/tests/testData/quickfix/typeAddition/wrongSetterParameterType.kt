// "Change setter parameter type to Int" "true"
class A() {
    var i: Int = 0
        set(v: <caret>Any) {}
}
/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeAccessorTypeFix