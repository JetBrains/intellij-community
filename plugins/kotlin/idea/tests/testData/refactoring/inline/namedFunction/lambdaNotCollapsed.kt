fun oldFun(p1: String, p2: () -> Boolean) {
    newFun(p1, null, p2)
}

fun newFun(p1: String, p2: String?, p3: () -> Boolean) {}

fun foo() {
    ol<caret>dFun("a") { true }
}