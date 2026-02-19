fun oldFun(p1: String, p2: Int = p1.length, p3: String? = p1) {
    newFun(p1, p2, p3)
}

fun newFun(x: String, y: Int = x.length, z: String? = "z") {}

fun foo() {
    <caret>oldFun("a")
}