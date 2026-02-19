class C {
    private val myX = ""

    val x: String
        get() {
            println("getter invoked")
            return myX
        }
}
