class J {
    private fun test() {
        val b = bool()
                && bool()

        if (bool()
            || bool()
        ) {
        }

        while (bool()
            && bool()
        ) {
        }

        takesBool(
            bool()
                    || bool()
        )
    }

    private fun bool(): Boolean {
        return true
    }

    private fun takesBool(b: Boolean) {
    }
}
