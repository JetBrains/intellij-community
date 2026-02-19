data class XYZ(val /*rename*/x: Int, val y: Int) {
    operator fun component1(): Int = x
}

fun test(xyz: XYZ) {
    val (a, b) = xyz
    val c = a + b
}