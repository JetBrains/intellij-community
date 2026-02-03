class J {
    fun test1(s: String?): String {
        return if (s != null) "true" else "false"
    }

    fun test2(s: String?): String {
        return if (s
            !=
            null
        ) "true" else "false"
    }

    fun test3(s: String?): String {
        return if (s != null)
            "true"
        else
            "false"
    }
}
