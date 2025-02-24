// "Change to 'var'" "false"
// ERROR: This annotation is not applicable to target 'local variable'
// WITH_STDLIB
// K2_AFTER_ERROR: This annotation is not applicable to target 'local variable'. Applicable targets: field
class Foo {
    fun foo() {
        <caret>@Volatile
        val bar: String = ""
    }
}
