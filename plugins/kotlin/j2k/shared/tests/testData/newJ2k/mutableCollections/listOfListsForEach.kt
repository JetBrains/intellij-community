class J {
    fun foo() {
        val list: MutableList<MutableList<Int>> = ArrayList<MutableList<Int>>()
        for (l in list) {
            l.add(42)
        }
    }
}
