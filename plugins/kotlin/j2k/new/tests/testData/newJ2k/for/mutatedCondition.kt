class J {
    fun test1() {
        var lastIndex = 5
        var i = 0
        while (i < lastIndex) {
            lastIndex--
            println(lastIndex)
            i++
        }
    }

    fun test2() {
        var lastIndex = 5
        var i = 0
        while (i < (1 + 2 + lastIndex)) {
            lastIndex--
            println(lastIndex)
            i++
        }
    }
}
