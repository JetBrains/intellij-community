class J {
    private fun test(strings: ArrayList<String>) {
        val strings1 = strings
        val strings2 = strings1

        for (s in strings2) {
            println(s.hashCode())
        }
    }
}
