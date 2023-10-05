class J {
    private var something: String? = null
    fun getSomething(): String? {
        return something
    }

    @Ann
    fun setSomething(s: String?) {
        something = s
    }
}
