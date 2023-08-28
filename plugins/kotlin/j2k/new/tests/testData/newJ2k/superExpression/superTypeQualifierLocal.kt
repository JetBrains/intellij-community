// ERROR: 'A' is an interface so it cannot be local. Try to use anonymous object or abstract class instead
// ERROR: 'B' is an interface so it cannot be local. Try to use anonymous object or abstract class instead
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
