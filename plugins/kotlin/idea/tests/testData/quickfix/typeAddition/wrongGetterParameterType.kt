// "Change getter type to Int" "true"
class A() {
    val i: Int
        get(): <caret>Any = 1
}
/* IGNORE_FIR */
