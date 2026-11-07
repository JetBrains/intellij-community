internal class C {
    private fun foo(map: MutableMap<Int?, String>): String {
        return map.getOrDefault(1, "bar")
    }
}
