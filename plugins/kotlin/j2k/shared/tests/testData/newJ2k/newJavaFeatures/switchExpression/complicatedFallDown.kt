package demo

object SwitchDemo {
    fun print(o: Any?) {
        println(o)
    }

    fun test(i: Int) {
        var monthString = "<empty>"
        val x = when (i) {
            1 -> {
                print(1)
                print(2)
                print(3)
                print(4)
                print(5)
                5
            }

            2 -> {
                print(2)
                print(3)
                print(4)
                print(5)
                5
            }

            3 -> {
                print(3)
                print(4)
                print(5)
                5
            }

            4 -> {
                print(4)
                print(5)
                5
            }

            5 -> {
                print(5)
                5
            }

            6 -> {
                print(6)
                print(7)
                print(8)
                print(9)
                print(10)
                print(11)
                monthString = "December"
                12
            }

            7 -> {
                print(7)
                print(8)
                print(9)
                print(10)
                print(11)
                monthString = "December"
                12
            }

            8 -> {
                print(8)
                print(9)
                print(10)
                print(11)
                monthString = "December"
                12
            }

            9 -> {
                print(9)
                print(10)
                print(11)
                monthString = "December"
                12
            }

            10 -> {
                print(10)
                print(11)
                monthString = "December"
                12
            }

            11 -> {
                print(11)
                monthString = "December"
                12
            }

            12 -> {
                monthString = "December"
                12
            }

            else -> {
                monthString = "Invalid month"
                13
            }
        }
        println(monthString)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        for (i in 1..12) test(i)
    }
}
