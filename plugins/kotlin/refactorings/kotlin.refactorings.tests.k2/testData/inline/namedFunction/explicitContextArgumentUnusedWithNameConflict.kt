// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Session(val user: String)

context(session: Session)
fun unusedContext(): String {
    val s = Session("inner")
    return s.user
}

fun caller(s: Session): String = unused<caret>Context(session = s)

