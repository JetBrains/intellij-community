// WITH_DEFAULT_VALUE: false

fun nestedAnonymousClasses() {
    val runnable = object : Runnable {
        override fun run() {
            val anotherRunnable = object : Runnable {
                override fun run() {
                    <selection>print("hello")</selection>
                }
            }
            anotherRunnable.run()
        }
    }
    runnable.run()
}