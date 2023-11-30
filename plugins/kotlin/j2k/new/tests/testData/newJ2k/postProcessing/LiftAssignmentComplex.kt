internal object Test {
    fun test(i: Int) {
        val x: Int
        if (i == 1) {
            println(1)
            x = 1
        } else if (i == 2) {
            println(2)
            x = 2
        } else {
            x = 3
        }
    }
}
