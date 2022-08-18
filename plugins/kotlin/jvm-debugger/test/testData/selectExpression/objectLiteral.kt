fun foo() {
    ob<caret>ject : Runnable {
        override fun run() {
            print("foo")
        }
    }
}

// EXPECTED_LEGACY: null