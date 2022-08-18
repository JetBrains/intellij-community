fun <T> <caret>dnnFun(otherVal: List<T & Any>) {}

fun test() {
    dnnFun(listOf(1, 2, 3))
}