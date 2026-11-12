// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Tx(val id: String)
class Session(val user: String)
class Request(val body: String)

context(tx: Tx, session: Session)
fun save(req: Request): String = "[${tx.id}:${session.user}] ${req.body}"

context(tx: Tx, session: Session)
fun withLocal(step: String): String {
    val s = Session("inner")
    return save(req = Request(step + ":" + s.user))
}

class Holder(val s: Session) {
    fun b1(): String = wit<caret>hLocal(tx = Tx("b1"), session = s, step = "x")
}