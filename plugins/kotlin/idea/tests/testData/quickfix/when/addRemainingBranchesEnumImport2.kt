// "Add remaining branches with * import" "true"
// WITH_STDLIB
import Foo.*

enum class Foo {
    A, B, C
}

class Test {
    fun foo(e: Foo) {
        when<caret> (e) {
            A, Foo.B -> TODO()
        }
    }
}
/* IGNORE_FIR */
