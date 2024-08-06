// "Convert supertype to '(String, T) -> Unit'" "true"
@Target(AnnotationTarget.TYPE)
annotation class TestA
@Target(AnnotationTarget.TYPE)
annotation class TestB
@Target(AnnotationTarget.TYPE)
annotation class TestC

class Foo<T> : (@TestA <caret>String).(@TestB T) -> @TestC Unit {
    override fun invoke(p1: String, p2: T) {
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertExtensionToFunctionTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertExtensionToFunctionTypeFix