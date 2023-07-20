// "Specify super type 'Foo' explicitly" "true"
// ERROR: Abstract member cannot be accessed directly
package three

import two.Derived

class Derived3 : Derived() {

    override fun check(): String {
        return super.<caret>check()
    }
}
