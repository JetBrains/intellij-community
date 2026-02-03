class J {
    private fun test(): Boolean {
        if (1
            + 1 == 2
        ) {
        }

        takesBool(
            1
                    + 1 == 2
        )

        while (1
            + 1 == 2
        ) {
        }

        // parentheses are required
        return (1
                + 1 == 2)
    }

    private fun takesBool(b: Boolean) {
    }
}
