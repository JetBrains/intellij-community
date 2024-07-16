object SwitchDemo {
    fun test(i: Int): Int {
        var monthString = "<empty>"
        val a = when (i) {
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
                13
            }

            7 -> {
                print(7)
                print(8)
                print(9)
                print(10)
                print(11)
                monthString = "December"
                13
            }

            8 -> {
                print(8)
                print(9)
                print(10)
                print(11)
                monthString = "December"
                13
            }

            9 -> {
                print(9)
                print(10)
                print(11)
                monthString = "December"
                13
            }

            10 -> {
                print(10)
                print(11)
                monthString = "December"
                13
            }

            11 -> {
                print(11)
                monthString = "December"
                13
            }

            12 -> {
                monthString = "December"
                13
            }

            else -> {
                print(4)
                print(5)
                5
            }
        }
        val status = ""
        val b: Int = return when (status) {
            "init", "dial", "transmit" -> 0x111111
            "ok" -> -0xff9a00
            "cancel" -> -0x99999a
            "fail", "busy", "error" -> -0x9a0000
            else -> -0x9a0000
        }
    }
}
