// JVM_TARGET: 17
@JvmRecord
data class Rectangle(val length: Double, val width: Double) {
    constructor() : this(42.0, 42.0)
}
