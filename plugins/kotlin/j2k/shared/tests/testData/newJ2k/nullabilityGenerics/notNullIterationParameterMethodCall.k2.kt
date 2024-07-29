internal class TestMethodCall {
    fun test() {
        for (s in returnStrings()) {
            println(s.length)
        }
    }

    private fun returnStrings(): Iterable<String> {
        return ArrayList<String>()
    }
}
