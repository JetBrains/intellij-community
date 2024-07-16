// Stubbed from Android Activity
internal class Activity {
    fun runOnUiThread(action: Runnable) {
        action.run()
    }
}

class Foo {
    private val activity: Activity? = null

    fun foo() {
        synchronized(this) {
            activity!!.runOnUiThread(object : Runnable {
                override fun run() {
                }
            })
        }
    }

    fun bar() {
        synchronized(this) {
            activity!!.runOnUiThread(object : Runnable {
                override fun run() {
                }
            })
        }
    }
}
