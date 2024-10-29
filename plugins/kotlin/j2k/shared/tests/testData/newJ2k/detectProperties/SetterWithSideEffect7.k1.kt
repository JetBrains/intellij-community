class C {
    private var xx = ""

    var x: String
        get() = xx
        set(value) {
            println("setter invoked")
            xx = value
        }
}
