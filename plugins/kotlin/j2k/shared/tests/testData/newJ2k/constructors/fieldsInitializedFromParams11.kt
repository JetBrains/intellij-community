class Bug(p: String, b: Boolean) {
    private val s: String

    init {
        if (b) {
            s = p.trim()
        } else s = p
    }
}
