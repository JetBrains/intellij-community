class Holder {
    companion object {
        fun beta(x: Int): Int = x
    }

    fun beta(x: Int): Int = x
}

fun outside(): Int = Holder().beta(1)
