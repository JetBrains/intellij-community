class J {
    private var something: String? = null

    @Ann
    fun getSomething(): String? {
        return something
    }

    fun setSomething(s: String?) {
        something = s
    }
}
