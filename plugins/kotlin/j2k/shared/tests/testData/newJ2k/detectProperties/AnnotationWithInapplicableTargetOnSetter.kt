class J {
    private var x: String? = null
    fun getX(): String? {
        return x
    }

    @Inapplicable
    fun setX(s: String?) {
        x = s
    }

    @set:Applicable
    var y: String? = null
}
