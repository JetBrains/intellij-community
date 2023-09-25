class Foo {
    var state: Int? = null
        private set

    fun setState(state: Int) {
        this.state = state
        if (state == 1) println("1")
    }
}
