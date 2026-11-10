// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Tx(val id: String)
class Session(val user: String)

context(_: Session)
fun audit(x: String): String = x + contextOf<Session>().user

context(tx: Tx)
fun id(): String = tx.id

context(tx: Tx, session: Session)
fun outer(): String = audit(id())

fun caller(): String = out<caret>er(tx = Tx("t"), session = Session("s"))
