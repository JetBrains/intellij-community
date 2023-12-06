internal enum class E {
    A, B, C
}

internal class A {
    fun foo(list: List<String?>, collection: Collection<Int?>, map: Map<Int, Int?>) {
        val a = "".length
        val b = E.A.name
        val c = E.A.ordinal
        val d = list.size + collection.size
        val e = map.size
        val f = map.keys.size
        val g = map.values.size
        val h = map.entries.size
        val i = map.entries.iterator().next().key + 1
    }

    fun bar(list: MutableList<String?>, map: HashMap<String, Int>) {
        val c = "a"[0]
        val b = 10.toByte()
        val i = 10.1.toInt()
        val f = 10.1.toFloat()
        val l = 10.1.toLong()
        val s = 10.1.toInt().toShort()

        try {
            val removed = list.removeAt(10)
            val isRemoved = list.remove("a")
        } catch (e: Exception) {
            System.err.println(e.message)
            throw RuntimeException(e.cause)
        }

        for (entry in map.entries) {
            val key = entry.key
            val value = entry.value
            entry.setValue(value + 1)
        }
    }

    fun primitiveConversions(bool: Boolean, b: Byte, s: Short, i: Int, l: Long, f: Float, d: Double, str: String) {
        bool
        b
        s
        i
        l
        f
        d

        str.toBoolean()
        str.toBoolean()

        str.toByte()
        str.toByte()
        str.toByte(i)
        str.toByte(i)

        str.toShort()
        str.toShort()
        str.toShort(i)
        str.toShort(i)

        str.toInt()
        str.toInt()
        str.toInt(i)
        str.toInt(i)

        str.toLong()
        str.toLong()
        str.toLong(i)
        str.toLong(i)

        Integer.parseInt(str, i, i, i)
        java.lang.Long.parseLong(str, i, i, i)

        str.toFloat()
        str.toFloat()

        str.toDouble()
        str.toDouble()
    }

    fun kt7940() {
        val b1 = Byte.MIN_VALUE
        val b2 = Byte.MAX_VALUE
        val s1 = Short.MIN_VALUE
        val s2 = Short.MAX_VALUE
        val i1 = Int.MIN_VALUE
        val i2 = Int.MAX_VALUE
        val l1 = Long.MIN_VALUE
        val l2 = Long.MAX_VALUE
        val f1 = Float.MIN_VALUE
        val f2 = Float.MAX_VALUE
        val f3 = Float.POSITIVE_INFINITY
        val f4 = Float.NEGATIVE_INFINITY
        val f5 = Float.NaN
        val d1 = Double.MIN_VALUE
        val d2 = Double.MAX_VALUE
        val d3 = Double.POSITIVE_INFINITY
        val d4 = Double.NEGATIVE_INFINITY
        val d5 = Double.NaN
    }

    fun kt35593() {
        val number: Number = 1
        val b = number.toByte()
        val d = number.toDouble()
        val f = number.toFloat()
        val i = number.toInt()
        val l = number.toLong()
        val s = number.toShort()
    }
}
