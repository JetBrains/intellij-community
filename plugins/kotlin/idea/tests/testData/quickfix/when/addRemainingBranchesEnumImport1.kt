// "Add remaining branches with * import" "true"
// WITH_STDLIB
enum class Foo {
    A, B, C
}

class Test {
    fun foo(e: Foo) {
        when<caret> (e) {
        }
    }
}
/* IGNORE_FIR */
