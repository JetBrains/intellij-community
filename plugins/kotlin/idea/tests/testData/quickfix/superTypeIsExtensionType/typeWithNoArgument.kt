// "Convert supertype to '(String) -> Unit'" "true"

class Foo : <caret>String.() -> Unit {
    override fun invoke(p1: String) {
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertExtensionToFunctionTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertExtensionToFunctionTypeFix