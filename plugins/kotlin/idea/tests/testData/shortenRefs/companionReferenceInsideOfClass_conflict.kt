package test

class Foo {
    fun myFun() {}

    companion object {
        fun myFun() {}
    }

    fun usage() {
        <selection>Foo.Companion.myFun()</selection>
    }
}