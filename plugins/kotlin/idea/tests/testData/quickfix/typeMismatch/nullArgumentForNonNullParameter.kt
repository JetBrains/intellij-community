// "Change parameter 's' type of primary constructor of class 'Foo' to 'String?'" "true"
class Foo(s: String) {

}

fun test() {
    Foo(<caret>null)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
/* IGNORE_K2 */