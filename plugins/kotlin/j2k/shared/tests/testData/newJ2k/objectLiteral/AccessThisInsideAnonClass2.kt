class Foo {
    fun fail() {
        object : Test.FrameCallback {
            override fun doFrame() {
                Test().postFrameCallbackDelayed(this)
            }
        }
    }
}

internal class Test {
    interface FrameCallback {
        fun doFrame()
    }

    fun postFrameCallbackDelayed(callback: FrameCallback?) {
    }
}
