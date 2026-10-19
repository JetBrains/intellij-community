fun oldFun(p: Int) {
    newFun(p, "x", "y")
}

fun newFun(p: Int, vararg v: String) {}

fun test() {
    <caret>oldFun(p = 1)
}
