class J {
    fun foo() {
        val list: List<MutableList<Int>> = ArrayList<MutableList<Int>>()
        for (l in list) {
            l.add(42)
        }
    }
}
