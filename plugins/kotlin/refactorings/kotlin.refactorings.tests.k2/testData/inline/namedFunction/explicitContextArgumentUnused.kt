// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Tx(val id: String)
class Session(val user: String)

fun newSession(): Session = Session("new")

context(tx: Tx)
fun txId(): String = tx.id

context(tx: Tx, session: Session)
fun onlyTx(): String = txId()

fun caller(): String = only<caret>Tx(tx = Tx("caller"), session = newSession())
