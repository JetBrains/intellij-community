package switch_demo

object SwitchDemo {
    @JvmStatic
    fun main(args: Array<String>) {
        val month = 8
        val monthString: String
        val a = when (month) {
            1 -> {
                monthString = "January"
                1
            }

            2 -> {
                monthString = "February"
                2
            }

            3 -> {
                monthString = "March"
                3
            }

            4 -> {
                monthString = "April"
                4
            }

            5 -> {
                monthString = "May"
                5
            }

            6 -> {
                monthString = "June"
                6
            }

            7 -> {
                monthString = "July"
                7
            }

            8 -> {
                monthString = "August"
                8
            }

            9 -> {
                monthString = "September"
                9
            }

            10 -> {
                monthString = "October"
                10
            }

            11 -> {
                monthString = "November"
                11
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
}
