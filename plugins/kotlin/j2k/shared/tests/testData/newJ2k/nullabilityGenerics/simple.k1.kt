class J {
    fun foo(list: List<String>, map: Map<String?, String?>) {
        val s1 = list[0]
        val s2 = map[""]

        s1.length // not-null assertion
        s2!!.length // not-null assertion
    }
}
