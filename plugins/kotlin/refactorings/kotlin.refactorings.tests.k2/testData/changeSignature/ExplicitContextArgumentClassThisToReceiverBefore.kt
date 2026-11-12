// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2
// CHECK_ERRORS_AFTER

context(session: Session)
fun gre<caret>et(): String = "hello"

class Session {
    fun call(): String = greet(session = this)
}
