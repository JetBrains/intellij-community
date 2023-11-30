class AAA {
    var x: Int = 42
        private set

    fun setX(x: Int): Int {
        this.x = x
        return x
    }
}
