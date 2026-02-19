fun oldFun(p1: String, vararg p2: Int){
    newFun(p1, *p2)
}

fun newFun(p1: String, vararg p2: Int){}

fun foo() {
    ol<caret>dFun("a")
}