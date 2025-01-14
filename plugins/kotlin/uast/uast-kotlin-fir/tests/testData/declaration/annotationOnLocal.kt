package test.pkg

fun test(x: Any?) {
    @Suppress("SdCardPath1")
    fun localFunction() {
        x?.toString()
    }

    localFunction()

    val lambda = @Suppress("SdCardPath2") {
        x?.toString()
    }

    lambda()
}