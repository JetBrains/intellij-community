data class XYZ(val /*rename*/value: Int, val y: Int) {
    operator fun component1(): Int = value
}

fun test(xyz: XYZ) {
    val (a, b) = xyz
    val c = a + b
}