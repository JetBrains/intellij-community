// WITH_STDLIB
// REPLACE_SINGLE_OCCURRENCE
// SPECIFY_TYPE_EXPLICITLY
// IGNORE_K1
class X {
    val x: Any = 1
}

fun f2(x: X) {
    if (x.x is String) {
        x.x<caret>
    }
}
