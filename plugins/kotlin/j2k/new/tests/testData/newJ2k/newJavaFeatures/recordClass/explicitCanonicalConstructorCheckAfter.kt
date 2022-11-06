// JVM_TARGET: 17
@JvmRecord
data class R(val x: Int) {
    init {
        if (x <= 0) throw RuntimeException()
    }
}
