class C {
    private var myX: String? = ""

    var x: String?
        get() {
            println("getter invoked")
            return myX
        }
        set(x) {
            this.myX = x
        }

    fun foo() {
        println("myX = " + myX)
    }
}
