data class XYZ(val p1: Int, val y: Int) {
    operator fun component1(): Int = p1
}

fun test(xyz: XYZ) {
    val (a, b) = xyz
    val c = a + b
}