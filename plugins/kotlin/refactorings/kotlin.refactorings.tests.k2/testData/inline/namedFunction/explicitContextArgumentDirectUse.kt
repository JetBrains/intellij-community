// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Session(val user: String)

context(session: Session)
fun user(): String = session.user

fun caller(other: Session): String = us<caret>er(session = other)
