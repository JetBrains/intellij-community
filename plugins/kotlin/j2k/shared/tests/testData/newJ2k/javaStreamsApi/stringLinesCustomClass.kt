class String {
    fun lines() {}

    companion object {
        fun foo() {
            val s = String()
            s.lines()
        }
    }
}
