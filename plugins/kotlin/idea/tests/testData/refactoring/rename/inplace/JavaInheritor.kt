// NEW_NAME: run1
// RENAME: member
// SHOULD_FAIL_WITH: Cannot perform refactoring. This element cannot be renamed

fun runnableInside() {
    val runnable = object : Runnable {
        override fun run() {
            val anotherRunnable = object : Runnable {
                override fun <caret>run() {
                    print("hello")
                }
            }
            anotherRunnable.run()
        }
    }
    runnable.run()
}