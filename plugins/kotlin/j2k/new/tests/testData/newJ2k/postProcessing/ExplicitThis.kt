class J {
    var i: Int = 0
    fun foo() {
        var x = this.i
        x = this.i * -this.i
        x = if (this.i > 0) this.i else this.i
        this.i = x
        println(this.i)
        this.foo()
    }
}
