internal class J1 {
    fun run() {
        problem()

        object : Runnable {
            val x: Int = 0
            var y: Int = 0

            override fun run() {
                x + y
                problem()
            }
        }
    }

    companion object {
        fun problem() {
        }
    }
}

internal object J2 {
    private val foo: Runnable = object : Runnable {
        val x: Int = 0
        var y: Int = 0

        override fun run() {
            x + y
            problem()
        }

        fun problem() {
        }
    }
}
