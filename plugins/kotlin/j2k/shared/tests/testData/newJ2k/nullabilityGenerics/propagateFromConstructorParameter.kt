class C {
    private val field = HashMap<String, String>()

    fun foo() {
        D(field)
    }
}

internal class D(param: HashMap<String, String>?)
