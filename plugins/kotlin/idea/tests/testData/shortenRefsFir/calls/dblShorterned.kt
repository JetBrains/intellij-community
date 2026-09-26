class X {
    fun oldFun(p: Any) {
    }

    fun newFun(p: Any){}
}

fun X.foo() {
    <selection>this.oldFun(this.toString())</selection>
}