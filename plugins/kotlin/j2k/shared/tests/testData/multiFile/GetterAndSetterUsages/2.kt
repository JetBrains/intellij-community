// ERROR: Unresolved reference 'getX' on receiver of type 'AAA'.
// ERROR: Unresolved reference 'setX' on receiver of type 'AAA'.
package test

class B {
    fun foo(a: AAA) {
        a.setX(a.getX() + 1)
        this.yY += "a"
    }

    var yY: String = ""
        private set
}
