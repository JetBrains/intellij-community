// "Convert supertype to '(String, String) -> Unit'" "true"
// K2_ERROR: Extension or contextual function type is not allowed as a supertype.

class Foo : <caret>String.(String) -> Unit {
    override fun invoke(p1: String, p2: String) {
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertExtensionToFunctionTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertExtensionToFunctionTypeFix