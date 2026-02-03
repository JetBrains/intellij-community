// JVM_TARGET: 17
@JvmRecord
data class J(val x: Int) {
    fun i(y: Int) {}

    fun f1() {
        i(x + this.x + this.x + this.x)
    }

    fun f2(x: Int) {
        i(x + this.x + this.x + this.x)
    }

    fun f3(j: J) {
        i(j.x + j.x)
    }

    fun f4() {
        val j = J(42)
        f3(J(42))
        f3(j)
    }
}
