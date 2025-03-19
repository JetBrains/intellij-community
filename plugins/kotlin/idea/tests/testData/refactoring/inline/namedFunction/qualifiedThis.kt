interface I {
    fun oldFun(s: String) {
        newFun(this, s)
    }
}

fun newFun(i: I, s: String){}

fun I.foo() {
    with("a") {
        <caret>oldFun(this)
    }
}
