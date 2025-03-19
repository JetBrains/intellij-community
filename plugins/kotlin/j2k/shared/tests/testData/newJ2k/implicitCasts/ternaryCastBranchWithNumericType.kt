class J {
    fun literalThen() {
        val a = 3.0
        val b = if (a != 0.0) 1.0 else a
    }

    fun literalElse() {
        val a = 3.0
        val b = if (a != 0.0) a else 1.0
    }

    fun expressionThen() {
        val a = 3.0
        val b = if (a != 0.0) (1 + 2 * 3).toDouble() else a
    }

    fun expressionElse() {
        val a = 3.0
        val b = if (a != 0.0) a else (1 + 2 * 3).toDouble()
    }


    fun sameType() {
        val a = 3.0
        val b = if (a != 0.0) 1.0 else a
    }
}
