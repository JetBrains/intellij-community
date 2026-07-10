// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Transaction(val id: String)
class Session(val userId: String)

context(tx: Transaction, session: Session)
fun <T> save(block: () -> T): T = block()

context(tx: Transaction, session: Session)
fun process(step: String): String = save { "[${tx.id}:${session.userId}] inline:$step" }

fun test() {
    val tx = Transaction("tx-1")
    val session = Session("user-1")

    val result = process<caret>(tx = tx, session = session, step = "run")
}
