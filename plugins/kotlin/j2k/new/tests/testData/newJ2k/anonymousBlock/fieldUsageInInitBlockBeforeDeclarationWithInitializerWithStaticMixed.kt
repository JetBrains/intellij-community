internal class C {
    var a = 0

    init {
        a = 2
    }

    init {
        a = 4
    }

    var c = 4

    init {
        a++
        b++
    }

    init {
        println(c)
        b = 2
    }

    companion object {
        var b = 0

        init {
            b++
        }

        init {
            b = 0
        }
    }
}
