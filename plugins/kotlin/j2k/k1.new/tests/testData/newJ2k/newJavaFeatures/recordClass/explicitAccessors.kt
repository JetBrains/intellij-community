// JVM_TARGET: 17
@JvmRecord
data class R(val x: Int) {
    fun x(): Int {
        return if (this.x < 100) this.x else 100
    }
}
