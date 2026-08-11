// "Change to 'var'" "false"
// ERROR: This annotation is not applicable to target 'local variable'
// WITH_STDLIB
// K2_AFTER_ERROR: WRONG_ANNOTATION_TARGET
// K2_ERROR: WRONG_ANNOTATION_TARGET
class Foo {
    fun foo() {
        <caret>@Volatile
        val bar: String = ""
    }
}
