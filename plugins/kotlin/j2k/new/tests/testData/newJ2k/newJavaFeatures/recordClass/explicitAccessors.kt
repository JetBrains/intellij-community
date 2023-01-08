// JVM_TARGET: 17
@JvmRecord
data class R(val x: Int) {
    fun x(): Int {
        return if (x < 100) x else 100
    }
}
