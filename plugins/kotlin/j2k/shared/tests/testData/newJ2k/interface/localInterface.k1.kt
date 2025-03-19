// ERROR: 'A' is an interface so it cannot be local. Try to use anonymous object or abstract class instead
class J {
    fun foo() {
        interface A
    }
}
