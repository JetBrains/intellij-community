class Foo {
    private var value: String? = null
        get() {
            if (true) {
                field = "new"
            }
            return field
        }
}
