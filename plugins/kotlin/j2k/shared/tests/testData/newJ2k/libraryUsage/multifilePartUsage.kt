class JJ {
    private fun bug(list: List<String>): List<Int> {
        return list.map { string: String -> string.length }
    }
}
