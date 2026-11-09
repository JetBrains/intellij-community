class JJ {
    private fun bug(list: MutableList<String?>): List<Int?> {
        return list.map<String?, Int?> { string: String? -> string!!.length }
    }
}
