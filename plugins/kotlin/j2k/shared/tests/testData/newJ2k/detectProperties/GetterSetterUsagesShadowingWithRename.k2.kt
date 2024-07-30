class A {
    var item: String? = null

    fun f1(item: String?) {
        this.item = this.item + item
    }

    fun f2() {
        val item = this.item
        this.item = item
    }
}
