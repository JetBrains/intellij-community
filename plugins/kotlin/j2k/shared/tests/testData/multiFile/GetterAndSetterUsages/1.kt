// ERROR: UNRESOLVED_REFERENCE
package test

class AAA {
    @JvmField
    var x: Int = 42

    fun foo() {
        this.x = this.x + 1
    }

    fun bar(b: B) {
        println(b.getYY())
    }
}
