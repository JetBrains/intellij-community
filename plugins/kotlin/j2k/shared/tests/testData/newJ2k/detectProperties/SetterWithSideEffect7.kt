class C {
    private var xx: String? = ""

    var x: String?
        get() = xx
        set(value) {
            println("setter invoked")
            xx = value
        }
}
