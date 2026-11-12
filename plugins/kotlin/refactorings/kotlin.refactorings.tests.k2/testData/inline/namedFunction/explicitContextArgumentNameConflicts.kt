// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Tx(val id: String)
class Session(val user: String)
class Request(val body: String)

context(tx: Tx, session: Session)
fun save(req: Request): String = "[${tx.id}:${session.user}] ${req.body}"

context(tx: Tx, session: Session)
fun withLambdaParam(): String = listOf(Session("inner")).joinToString { s -> save(req = Request(s.user)) }

fun b2(s: Session): String = withLam<caret>bdaParam(tx = Tx("b2"), session = s)
