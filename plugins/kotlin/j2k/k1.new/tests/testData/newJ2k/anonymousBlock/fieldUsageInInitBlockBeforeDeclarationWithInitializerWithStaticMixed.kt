internal class C {
    var a: Int = 0

    init {
        a = 2
    }


    init {
        a = 4
    }

    var c: Int = 4

    init {
        a++
        b++
    }

    init {
        println(c)
        b = 2
    }

    companion object {
        var b: Int = 0

        init {
            b++
        }

        init {
            b = 0
        }
    }
}
