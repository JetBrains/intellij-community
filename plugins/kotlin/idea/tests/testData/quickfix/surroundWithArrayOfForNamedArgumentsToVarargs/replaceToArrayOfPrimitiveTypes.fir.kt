// "Surround with intArrayOf(...)" "true"

fun foo(vararg s: Int) {}

fun test() {
    foo(s = <caret>1)
}