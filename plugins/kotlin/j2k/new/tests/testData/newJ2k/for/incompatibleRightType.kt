object J {
    @JvmStatic
    fun main(args: Array<String>) {
        var i = 0
        while (i < 10.5) {
            println(i)
            i++
        }
        var j = 0
        while (j < 10.5f) {
            println(j)
            j++
        }
        var k = 0
        while (k < '5'.code) {
            println(k)
            k++
        }
    }
}
