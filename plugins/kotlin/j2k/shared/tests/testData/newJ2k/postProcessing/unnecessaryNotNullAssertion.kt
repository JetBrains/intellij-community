class J {
    fun foo(list: ArrayList<String?>) {
        for (s in list) {
            println(s!!.length)
            println(s.length)
            println(s.length)
        }
    }
}
