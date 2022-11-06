import java.util.Arrays
import java.util.List
import java.util.Set

class Collections {
    fun test() {
        val field = "A"
        val arr = arrayOf("A")
        val x1 = Arrays.asList(field)
        val x2 = Arrays.asList(*arr)
        val y1 = List.of(field)
        val y2 = List.of(*arr)
        val z1 = Set.of(field)
        val z2 = Set.of(*arr)
    }
}