// JVM_TARGET: 17
class Rectangle(length: Double, width: Double) {
    val length: Double
    val width: Double

    init {
        var length = length
        var width = width
        if (length <= 0 || width <= 0) throw RuntimeException()
        length = 2.0
        width *= 2.0
        this.length = length
        this.width = width
    }
}
