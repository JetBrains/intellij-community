// "Specify super type 'Foo' explicitly" "true"
// ERROR: Abstract member cannot be accessed directly
package three

import two.Derived

class Derived3 : Derived(), one.Foo {

    override fun check(): String {
        return super<Derived>.<caret>check()
    }
}
