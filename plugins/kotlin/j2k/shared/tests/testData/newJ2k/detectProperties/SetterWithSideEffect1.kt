class C {
    private var myX: String? = ""

    var x: String?
        get() = myX
        set(x) {
            println("setter invoked")
            this.myX = x
        }

    fun foo() {
        myX = "a"
    }
}
