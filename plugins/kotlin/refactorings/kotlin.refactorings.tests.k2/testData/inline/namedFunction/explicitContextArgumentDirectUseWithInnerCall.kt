// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Tx(val id: String)
class Session(val user: String)
class Request(val body: String)

context(tx: Tx, session: Session)
fun save(req: Request): String = "[${tx.id}:${session.user}] ${req.body}"

context(tx: Tx, session: Session)
fun describe(step: String): String {
    val u = session.user
    return "$u/" + save(req = Request(step))
}

fun caller(other: Session): String = desc<caret>ribe(tx = Tx("b1"), session = other, step = "s")
