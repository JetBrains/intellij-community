// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Session(val user: String)

context(session: Session)
fun inner(): String = session.user

context(session: Session)
fun outer(): String = inner()

fun caller(a: Any): String {
    if (a is Session) {
        return out<caret>er(session = a)
    }
    return ""
}
