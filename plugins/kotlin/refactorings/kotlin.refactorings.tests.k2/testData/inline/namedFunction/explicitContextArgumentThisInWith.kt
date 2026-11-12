// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Tx(val id: String)
class Session(val user: String)
class Request(val body: String)

context(tx: Tx, session: Session)
fun save(req: Request): String = "[${tx.id}:${session.user}] ${req.body}"

context(tx: Tx, session: Session)
fun withReceiver(): String = with(Request("inner")) { save(req = this) }

fun Session.b6(): String = withRecei<caret>ver(tx = Tx("b6"), session = this)
