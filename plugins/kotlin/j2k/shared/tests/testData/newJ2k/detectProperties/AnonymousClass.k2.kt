class X {
    fun foo() {
        val runnable: Runnable = object : Runnable {
            var value: Int = 10

            override fun run() {
                println(this.value)
            }
        }
    }
}
