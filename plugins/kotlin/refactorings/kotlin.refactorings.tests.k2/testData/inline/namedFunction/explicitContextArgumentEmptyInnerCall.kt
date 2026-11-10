// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Tx(val id: String)

context(tx: Tx)
fun txId(): String = tx.id

context(tx: Tx)
fun delegated(): String = txId()

fun caller(): String = dele<caret>gated(tx = Tx("caller"))
