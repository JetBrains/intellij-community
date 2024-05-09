// ERROR: Interface 'A' cannot be local. Try to use an anonymous object or abstract class instead.
// ERROR: Interface 'B' cannot be local. Try to use an anonymous object or abstract class instead.
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
