// ERROR: Using @JvmRecord is only allowed with -jvm-target 16 or later (or -jvm-target 15 with the -Xjvm-enable-preview flag enabled)
// JVM_TARGET: 17
package test

@JvmRecord
data class J(@JvmField val x: Int) {
    fun x(): Int {
        return if (x < 100) x else 100
    }
}
