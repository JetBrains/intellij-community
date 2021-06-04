internal object Test {
    var a = 0

    init {
        println("0")
        a = 1
    }

    init {
        println("1")
    }

    var c = 0

    init {
        println("2")
        println(c)
    }

    var b = 0

    init {
        println("3")
        b = 2
    }

    init {
        println("4")
        a = 2
    }
}
