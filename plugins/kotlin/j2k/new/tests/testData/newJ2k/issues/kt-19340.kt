package test.nullabilityOnClassBoundaries

class Item {
    private var s1: String? = null
    private var s2: String? = null

    fun set(s1: String?, s2: String?) {
        this.s1 = s1
        this.s2 = s2
    }
}

class Reader {
    fun readItem(n: Int): Item {
        val item = Item()
        item.set(readString(n), null)
        return item
    }

    fun readString(n: Int): String? {
        if (n <= 0) return null
        return n.toString()
    }
}
