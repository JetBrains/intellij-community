// ERROR: Cannot infer type for type parameter 'R'. Specify it explicitly.
// ERROR: Cannot infer type for type parameter 'T'. Specify it explicitly.
// ERROR: Unresolved reference 'someRunnable'.
class Test {
    fun someMethod() {
        var someRunnable: Runnable = object : Runnable {
            override fun run() {
                someRunnable.run()
            }
        }
    }
}
