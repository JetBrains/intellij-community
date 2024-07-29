internal class J {
    var stringsField: ArrayList<String> = ArrayList()

    fun test() {
        for (s in stringsField) {
            println(s.length)
        }
    }

    private fun returnStrings(): ArrayList<String> {
        return stringsField
    }
}
