// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

open class Session
class Admin : Session()

context(admin: Admin)
fun audit(): String = "admin"

context(session: Session)
fun outer(): String = if (session is Admin) audit() else ""

fun caller(s: Session): String = out<caret>er(session = s)
