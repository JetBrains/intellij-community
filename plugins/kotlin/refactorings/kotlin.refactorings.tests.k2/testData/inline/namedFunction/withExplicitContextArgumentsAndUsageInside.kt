// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Transaction(val id: String)
class Session(val userId: String)
class Request(val body: String)

context(tx: Transaction, session: Session)
fun save(req: Request): String = "[${tx.id}:${session.userId}] ${req.body}"

context(tx: Transaction, session: Session)
fun process(step: String): String {
    val s = session.userId
    return save(req = Request("inline:$step"))
}

context(tx: Transaction)
fun test() {
    val session = Session("user-1")

    val result = process<caret>(session = session, step = "run")
}
