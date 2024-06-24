class Test {
    var xs: List<String>? = null
    var strings: List<String>? = null

    fun typeParameterWeirdness() {
        xs = if (strings != null) ArrayList(strings) else ArrayList()
    }
}
