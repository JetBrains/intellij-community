// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Session(val user: String)

context(_: Session)
fun audit(): String = contextOf<Session>().user

context(session: Session)
fun audited(): String = audit()

fun caller(): String {
    fun context(value: Session, block: () -> String): String = "shadow"
    return audi<caret>ted(session = Session("caller"))
}
