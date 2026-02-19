// "Specify super type 'Foo' explicitly" "true"
// ERROR: Abstract member cannot be accessed directly
package three

import one.Foo
import two.Derived

class Derived3 : Derived(), Foo {

    override fun check(): String {
        return super<Foo>.check()
    }
}
