// ERROR: Return type mismatch: expected 'MutableList<Int?>', actual 'List<Int?>'.
class JJ {
    private fun bug(list: MutableList<String?>): MutableList<Int?> {
        return list.map<String?, Int?> { string: String? -> string!!.length }
    }
}
