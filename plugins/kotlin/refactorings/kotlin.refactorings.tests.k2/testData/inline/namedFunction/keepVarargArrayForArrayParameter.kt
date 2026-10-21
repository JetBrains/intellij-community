fun oldFun(vararg values: Int) = newFun(values)

fun newFun(values: IntArray) {
}

fun test() {
    old<caret>Fun(1, 2)
}
