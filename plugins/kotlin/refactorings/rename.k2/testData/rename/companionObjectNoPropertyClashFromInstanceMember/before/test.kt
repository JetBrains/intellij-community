class Holder {
    companion object {
        val p: Int = 0
    }

    val /*rename*/q: Int = 1
}

fun outside(): Int = Holder().q
