internal class C(private var s: String?) {
    init {
        if (s == null) {
            print("null")
        }
    }
}
