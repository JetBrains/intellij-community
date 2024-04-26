// NEW_NAME: emptyList
// RENAME: member
class A {
    val p = emptyList<String>()

    fun <K> <caret>m() {}
}