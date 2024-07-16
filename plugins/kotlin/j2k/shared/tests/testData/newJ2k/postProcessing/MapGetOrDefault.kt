internal class C {
    private fun foo(map: Map<Int, String>): String {
        return map.getOrDefault(1, "bar")
    }
}
