// TODO: KTIJ-20050 Unexpected Unit added after inline refactoring

fun fail1(p: String): Nothing { throw Exception(p) }
fun callFail(p: String?) {
    val s = p ?: fa<caret>il1("message")
    println(s)
}
