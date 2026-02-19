// NEW_NAME: T
// RENAME: member
class A<<caret>X> {
    fun m() {
        println(T)
    }

    companion object {
        const val T = ""
    }
}