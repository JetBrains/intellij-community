// "Change type of 'A.x' to '() -> Int'" "true"
class A {
    var x: Int
        get(): Int = if (true) { {42}<caret> } else { {24} }
        set(i: Int) {}
}
/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix