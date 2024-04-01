// "Change to 'var'" "false"
// ERROR: This annotation is not applicable to target 'local variable'
// WITH_STDLIB
class Foo {
    fun foo() {
        <caret>@Volatile
        val bar: String = ""
    }
}
