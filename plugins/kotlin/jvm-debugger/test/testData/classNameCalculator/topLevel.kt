/* Boo */
@file:JvmName("Boo")

fun foo() {
    fun bar() {
        print("bar")
    }

    fun baz() {
        print("baz")

        fun zoo() {
            print("zoo")
        }

        block /* Boo$foo$baz$1 */{
            zoo()
        }
    }

    val boo = /* Boo$foo$boo$1 */fun() {
        print("boo")
    }

    baz()
    bar()
    boo()
}

fun block(block: () -> Unit) {
    block()
}