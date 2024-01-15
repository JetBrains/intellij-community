// JVM_TARGET: 17
package test

@JvmRecord
data class J(@JvmField val x: Int) {
    fun x(): Int {
        return if (this.x < 100) this.x else 100
    }
}
