class C {
    var x: String? = ""
        set(x) {
            println("old value: " + field)
            field = x
        }
}
