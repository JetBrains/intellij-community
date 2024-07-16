// JVM_TARGET: 17
@JvmRecord
data class Rectangle(val length: Double, val width: Double) {
    init {
        if (length <= 0 || width <= 0) throw RuntimeException()
    }
}
