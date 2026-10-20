class Holder {
    companion object {
        fun beta(x: Int): Int = x
    }

    fun /*rename*/alpha(x: Int): Int = x
}

fun outside(): Int = Holder().alpha(1)
