class J {
    fun foo(list: MutableList<String>, map: MutableMap<String?, String>) {
        val s1 = list.get(0)
        val s2: String = map.get("")!!

        s1.length // not-null assertion
        s2.length // not-null assertion
    }
}
