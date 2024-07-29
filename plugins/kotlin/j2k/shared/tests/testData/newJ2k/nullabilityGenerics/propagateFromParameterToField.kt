class C {
    private val field = HashMap<String, String>()

    fun foo() {
        baz(field)
    }

    fun baz(param: HashMap<String, String>?) {
    }
}
