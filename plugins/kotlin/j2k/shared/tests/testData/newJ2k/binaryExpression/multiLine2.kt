class A {
    fun mixedOperators() {
        val x = (1
                + 1 - 2)

        val x2 = (1 + 1
                - 2)

        val x3 = (1
                + 1
                - 2
                + 3)

        val x4 = (1
                + 1
                - 2 + 3)

        val x5 = (1
                + 1 - 2
                + 3)
    }

    fun nestedParentheses() {
        val x = (1
                + 1) - 2

        val x2 = ((1 + 1)
                - 2)

        val x3 = ((1
                + (1
                - 2))
                + 3)

        val x4 = (1
                + ((1
                - 2) + 3))

        val x5 = (1
                + ((1 - 2)
                + 3))
    }
}
