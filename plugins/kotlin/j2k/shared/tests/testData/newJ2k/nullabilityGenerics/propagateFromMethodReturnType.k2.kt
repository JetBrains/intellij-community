// ERROR: Cannot access 'fun add(e: String!, elementData: (Array<Any!>..Array<out Any!>?), s: Int): Unit': it is private in 'java/util/ArrayList'.
// NOTE: wrong error message is KT-69090
internal class J {
    private val strings = ArrayList<String>()

    fun report(s: String?) {
        strings.add(s)
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
