internal class C {
    var a: Int = 10

    init {
        a = 14
    }

    init {
        a = 12
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            println(C().a) // prints 12
        }
    }
}
