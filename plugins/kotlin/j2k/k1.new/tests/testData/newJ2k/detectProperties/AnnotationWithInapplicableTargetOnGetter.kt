class J {
    private var x: String? = null

    @Inapplicable
    fun getX(): String? {
        return x
    }

    fun setX(s: String?) {
        x = s
    }

    @get:Applicable
    var y: String? = null
}
