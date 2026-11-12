// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2
// CHECK_ERRORS_AFTER

class Session

context(session: Session)
fun gre<caret>et(): String = "hello"

fun Session.call(): String = greet(session = this)
