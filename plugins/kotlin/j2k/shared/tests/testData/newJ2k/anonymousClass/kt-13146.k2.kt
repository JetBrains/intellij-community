class Test {
    var someRunnable: Runnable = object : Runnable {
        override fun run() {
            someRunnable.run()
        }
    }
}

class Test2 {
    private val someRunnable: Runnable = object : Runnable {
        override fun run() {
            someRunnable.run()
        }
    }
}

class Handler {
    fun postDelayed(r: Runnable?, time: Long) {}
}

class Test3 {
    private val handler = Handler()

    private val someRunnable: Runnable = object : Runnable {
        override fun run() {
            handler.postDelayed(someRunnable, 1000)
        }
    }
}
