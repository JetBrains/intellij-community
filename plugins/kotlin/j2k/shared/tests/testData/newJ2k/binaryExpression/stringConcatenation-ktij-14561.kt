object TestClass {
    private fun getCheckKey(category: String, name: String, createWithProject: Boolean): String {
        return "$category:$name:$createWithProject"
    }
}
