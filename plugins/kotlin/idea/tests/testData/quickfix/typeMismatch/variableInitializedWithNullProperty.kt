// "Change type of 'Foo.x' to 'String?'" "true"
class Foo {
    var x = null

    fun foo(condition: Boolean) {
        if (condition) {
            x = "abc"<caret>
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix