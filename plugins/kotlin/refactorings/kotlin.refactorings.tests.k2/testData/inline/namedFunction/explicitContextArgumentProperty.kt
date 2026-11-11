// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Session(val user: String)

context(_: Session)
val auditProp: String get() = "prop(${contextOf<Session>().user})"

context(session: Session)
fun auditedProp(): String = auditProp

fun b3(): String = audite<caret>dProp(session = Session("b3"))