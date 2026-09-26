package a

operator fun Int.get(p: Int) { }

fun use<caret>ArrayAccess() {
    0[0]
}