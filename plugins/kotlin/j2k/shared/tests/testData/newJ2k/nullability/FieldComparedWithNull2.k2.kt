internal class C(private var s: String?) {
    fun foo() {
        if (s != null) {
            print("not null")
        }
    }
}
