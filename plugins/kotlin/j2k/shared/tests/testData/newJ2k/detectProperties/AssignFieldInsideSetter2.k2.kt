class C {
    private var x: String? = ""
    var other: C? = null

    fun getX(): String? {
        return x
    }

    fun setX(x: String?) {
        println("setter invoked")
        if (other != null) {
            this.other!!.x = x
        }
        this.x = x
    }
}
