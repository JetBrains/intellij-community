internal class J {
    fun foo(): String {
        val s1 = if (bool())
            "true"
        else
            "false"

        val s2 = if (bool()) "true" else "false"

        val s3 = if (bool() // condition
        )
            "true" // true
        else
            "false" // false

        val s4 = if (bool())
            "true"
        else
            "false"

        val s5 =
            if (bool()) "true" else "false"

        val s6 =
            if (bool())
                "true"
            else
                "false"

        val s7 =
            if (bool())
                "true"
            else
                "false"

        val s8 =
            if (bool())
                "true"
            else
                "false"

        println(
            if (bool())
                "true" +
                        "true"
            else
                "false" + "false"
        )

        return if (bool())
            "true"
        else
            "false"
    }

    private fun bool(): Boolean {
        return false
    }
}
