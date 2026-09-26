class Bug(p: String, b: Boolean) {
    private val s = if (b) {
        p.trim()
    } else p
}
