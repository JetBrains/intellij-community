fun <T> oldFun(vararg elements: T) {
    newFun(*elements)
}

fun <T> newFun(vararg elements: T){}

fun foo() {
    old<caret>Fun<String>("a")
}