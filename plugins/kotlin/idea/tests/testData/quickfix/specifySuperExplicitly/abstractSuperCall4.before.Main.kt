// "Specify super type 'Foo' explicitly" "true"
package three

import two.Derived

class Derived3 : Derived() {

    override fun check(): String {
        return super.<caret>check()
    }
}
