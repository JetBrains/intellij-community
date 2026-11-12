// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// CHECK_ERRORS_AFTER

class Session

context(session: Session)
val gre<caret>eting: String
    get() = "hello"

fun Session.call(): String = greeting
