// NEW_NAME: T
// RENAME: member
fun <<caret>X> p() {
    fun T() {}
    T()
    val t : X? = null
}
