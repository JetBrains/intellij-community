// ERROR: LOCAL_INTERFACE_NOT_ALLOWED
// ERROR: LOCAL_INTERFACE_NOT_ALLOWED
class J {
    fun foo() {
        interface A {
            fun foo() {
            }
        }

        interface B {
            fun foo() {
            }
        }

        class C : A, B {
            override fun foo() {
                super<A>.foo()
            }
        }
    }
}
