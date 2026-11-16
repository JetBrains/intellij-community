// ERROR: EXPOSED_PARAMETER_TYPE
internal interface Foo

class Bar {
    fun test() {
        object : Foo {
            fun foo() {
                bug(this)
            }
        }
    }

    fun bug(foo: Foo) {
    }
}
