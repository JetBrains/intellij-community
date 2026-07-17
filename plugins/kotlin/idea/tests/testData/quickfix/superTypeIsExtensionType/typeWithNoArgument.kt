// "Convert supertype to '(String) -> Unit'" "true"
// K2_ERROR: SUPERTYPE_IS_EXTENSION_OR_CONTEXT_FUNCTION_TYPE

class Foo : <caret>String.() -> Unit {
    override fun invoke(p1: String) {
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertExtensionToFunctionTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertExtensionToFunctionTypeFix