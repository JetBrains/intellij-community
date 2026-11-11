// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Session(val user: String)

class Logger(val name: String) {
    context(_: Session)
    fun audit(): String = "$name(${contextOf<Session>().user})"

    fun chained(): Logger = this
}

context(session: Session)
fun auditedBy(logger: Logger): String = logger.audit()

fun caller(): String = audited<caret>By(Logger("L").chained(), session = Session("caller")).substring(42)
