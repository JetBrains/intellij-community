package test.pkg

class Test {
    fun foo(x : Any?) {
        @Suppress("SdCardPath")
        x?.toString()
            ?: "/sdcard/foo"
            ?: "redundant"
    }
}
