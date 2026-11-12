// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Tx(val id: String)
class Session(val user: String) {
    fun chained() = this
}
class Request(val body: String) {
    fun chained() = this
}

context(tx: Tx, session: Session)
fun save(req: Request): String = "[${tx.id}:${session.user}] ${req.body}"

context(tx: Tx, session: Session)
fun withReceiver(): String = with(Request("inner")) { save(req = this) }

fun Session.b6(): String = with<caret>Receiver(tx = Tx("b6"), session = chained())