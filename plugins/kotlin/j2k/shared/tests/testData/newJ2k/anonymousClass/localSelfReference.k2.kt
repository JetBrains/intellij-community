// ERROR: CANNOT_INFER_PARAMETER_TYPE
// ERROR: CANNOT_INFER_PARAMETER_TYPE
// ERROR: UNRESOLVED_REFERENCE
class Test {
    fun someMethod() {
        var someRunnable: Runnable = object : Runnable {
            override fun run() {
                someRunnable.run()
            }
        }
    }
}
