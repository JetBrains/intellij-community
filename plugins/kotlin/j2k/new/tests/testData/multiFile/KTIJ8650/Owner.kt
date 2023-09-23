class Owner {
    private var string: String? = null

    @Synchronized
    fun getString(): String {
        if (string == null) {
            string = ""
        }
        return string!!
    }

    @Synchronized
    fun getString(defaultValue: String?): String? {
        if (string == null) {
            string = defaultValue
        }
        return string
    }
}
