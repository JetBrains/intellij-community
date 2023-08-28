// NEW_NAME: foo
// RENAME: member
fun xyzzy(): Any {
    fun b<caret>ar(): Int {
        return@bar 1
    }
}
