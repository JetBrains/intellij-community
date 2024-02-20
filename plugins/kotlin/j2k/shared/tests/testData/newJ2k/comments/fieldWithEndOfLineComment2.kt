class Foo {
    private var someInt: Int? = null
    var state: Int?
        get() = someInt
        set(state) {
            //some comment 1
            someInt = state
            //some comment 2
            if (state == 2) println("2")
        }
}