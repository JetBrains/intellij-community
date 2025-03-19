/* ObjectLiteralsKt */fun foo() {
    /* ObjectLiteralsKt$foo$1 */object : Runnable [
        override fun run() {
            block /* ObjectLiteralsKt$foo$run$1 */{
                print("foo")
            }
        }
    ]
}

fun block(block: () -> Unit) {
    block()
}