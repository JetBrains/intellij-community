class J {
    private val s: String? = null

    fun foo() {
        var local = s
        if (local == null) {
            synchronized(this) {
                local = "local"
                println(local.length)
            }
        }
    }
}
