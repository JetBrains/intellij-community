// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Transaction(val id: String)
class Session(val userId: String)
class Request(val body: String)

context(t: Transaction, s: Session)
fun save(req: Request): String = "[${t.id}:${s.userId}] ${req.body}"

context(tx: Transaction, session: Session)
fun process(step: String): String = save(req = Request("inline:$step"))

fun test() {
    val tx = Transaction("tx-1")
    val session = Session("user-1")

    val result = process<caret>(tx = tx, session = session, step = "run")
}
