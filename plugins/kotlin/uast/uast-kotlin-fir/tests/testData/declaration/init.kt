class Foo(val s : String) {
    init {
        println("Foo.init")
    }

    constructor(i : Int): this ("$i") {
        println("Foo 2nd ctor")
    }
}

fun boo() {
    val o = object {
        init {
            println("object.init")
        }
    }
}