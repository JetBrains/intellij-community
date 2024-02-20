// ERROR: Unresolved reference: yy
package test

class AAA {
    @JvmField
    var x: Int = 42

    fun foo() {
        x = x + 1
    }

    fun bar(b: B) {
        println(b.yy)
    }
}
