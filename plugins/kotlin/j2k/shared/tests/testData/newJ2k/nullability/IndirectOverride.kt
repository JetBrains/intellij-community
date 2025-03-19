import javaApi.Derived

// !ADD_JAVA_API
internal class C : Derived() {
    override fun foo(s: String?): String? {
        return s
    }
}
