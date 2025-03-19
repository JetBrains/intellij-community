class JJ {
    fun foo(strings: MutableList<String?>) {
        for (string in strings) {
            var string = string
            string = ""
        }
    }
}
