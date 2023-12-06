/* SuspendKt */suspend fun foo() {
    suspend fun bar() {
        block /* SuspendKt$foo$bar$1 */{
            print("bar")
        }
    }

    block /* SuspendKt$foo$2 */{
        print("foo")

        block /* SuspendKt$foo$2$1 */{
            print("foo2")
        }
    }
}

fun block(block: () -> Unit) {
    block()
}