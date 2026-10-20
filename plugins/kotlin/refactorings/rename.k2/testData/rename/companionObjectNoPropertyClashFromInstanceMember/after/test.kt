class Holder {
    companion object {
        val p: Int = 0
    }

    val p: Int = 1
}

fun outside(): Int = Holder().p
