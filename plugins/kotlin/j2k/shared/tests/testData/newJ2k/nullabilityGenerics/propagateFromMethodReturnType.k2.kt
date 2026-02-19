internal class J {
    private val strings = ArrayList<String>()

    fun report(s: String?) {
        strings.add(s!!)
    }

    fun returnStrings(): ArrayList<String> {
        return strings // update return expression type from method return type
    }

    fun test() {
        for (s in returnStrings()) {
            println(s.length)
        }
    }
}
