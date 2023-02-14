class Test0xFFFF_FFFFL {
    fun readLong(index: Int): Long {
        val l1 = readInt(index).toLong()
        val l0 = readInt(index + 4).toLong() and 0xFFFFFFFFL
        return l1 + l0
    }

    fun readInt(index: Int): Int {
        return 0
    }
}
