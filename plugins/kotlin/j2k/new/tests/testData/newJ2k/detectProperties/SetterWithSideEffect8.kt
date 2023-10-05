class Foo {
    private var someInt: Int? = null

    var state: Int?
        get() = someInt
        set(state) {
            someInt = state
            if (state == 1) println("1")
        }
}
