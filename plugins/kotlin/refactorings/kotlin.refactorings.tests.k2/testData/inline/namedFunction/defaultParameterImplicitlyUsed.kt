fun oldFun(p1: String = "", p2: Int = 0) {
    newFun(p1, p2)
}

fun newFun(p1: String = "", p2: Int = 0, p3: Int = -1) {}

fun foo() {
    old<caret>Fun()
}
