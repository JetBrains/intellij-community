import java.util.Set

class Collections {
    fun test() {
        val x = Set.of("A", "A")
        val y = Set.of("A", null)
        val z = Set.of<String?>(null)
    }
}
