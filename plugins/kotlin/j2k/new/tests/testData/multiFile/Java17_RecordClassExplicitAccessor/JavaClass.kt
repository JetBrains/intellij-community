// JVM_TARGET: 17
package test

@JvmRecord
data class J(@JvmField val x: Int) {
    fun x(): Int {
        return if (x < 100) x else 100
    }
}
