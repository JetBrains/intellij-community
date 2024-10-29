class AAA {
    var x: Int = 42
        private set

    fun foo(other: AAA) {
        println(this.x)
        println(other.x)
        this.x = 10
    }
}
