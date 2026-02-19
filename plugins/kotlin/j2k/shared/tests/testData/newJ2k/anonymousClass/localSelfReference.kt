class Test {
    fun someMethod() {
        var someRunnable: Runnable = object : Runnable {
            override fun run() {
                this.run()
            }
        }
    }
}
