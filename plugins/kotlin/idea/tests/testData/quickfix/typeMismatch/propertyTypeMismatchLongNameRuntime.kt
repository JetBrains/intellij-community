// "Change type of 'f' to '(Delegates) -> Unit'" "true"
// WITH_STDLIB

fun foo() {
    var f: Int = { x: kotlin.properties.Delegates ->  }<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix