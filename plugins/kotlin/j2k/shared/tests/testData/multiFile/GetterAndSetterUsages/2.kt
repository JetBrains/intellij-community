// ERROR: Unresolved reference 'getX'.
// ERROR: Unresolved reference 'setX'.
package test

class B {
    fun foo(a: AAA) {
        a.setX(a.getX() + 1)
        this.yY += "a"
    }

    var yY: String = ""
        private set
}
