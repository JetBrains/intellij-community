fun oldFun(p1: String = "", p2: Int = 0, handler: () -> Unit) {
    newFun(p2, p1, handler)
}

fun newFun(a: Int = 0, b: String = "", handler: () -> Unit) {}

fun foo() {
    oldF<caret>un { }
}