// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Session(val user: String)

context(session: Session)
infix fun String.by(who: String): String = "$this by $who@${session.user}"

context(session: Session)
fun signed(): String = "doc" by "me"

context(outer: Session)
fun caller(): String = sig<caret>ned(session = Session("explicit"))
