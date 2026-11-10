// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Session(val user: String)
class Tx(val id: String)

context(tx: Tx, session: Session)
fun save(): String = "${tx.id}:${session.user}"

context(tx: Tx, session: Session)
fun withLocal(): String {
    val s = Session("inner")
    return save() + s.user
}

fun caller(s: Session): String = with<caret>Local(tx = Tx("caller"), session = s)
