// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

class Session(val user: String)

fun newSession(): Session = Session("new")

context(session: Session)
fun save(): String = session.user

context(session: Session)
fun twice(): String = save() + save()

fun caller(): String = tw<caret>ice(session = newSession())
