class JJ {
    fun foo(strings: List<String?>) {
        for (string in strings) {
            var string = string
            string = ""
        }
    }
}
