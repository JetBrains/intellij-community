// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Tx(val id: String)

context(tx: Tx)
inline fun <reified T> tag(value: T): String = "${tx.id}:${T::class.simpleName}"

context(tx: Tx)
fun tagged(): String = tag<Number>(42)

fun caller(): String = tag<caret>ged(tx = Tx("caller"))
