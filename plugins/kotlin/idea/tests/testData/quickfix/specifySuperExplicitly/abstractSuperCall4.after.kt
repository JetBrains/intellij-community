// "Specify super type 'Foo' explicitly" "true"
package three

import one.Foo
import two.Derived

class Derived3 : Derived(), Foo {

    override fun check(): String {
        return super<Foo>.check()
    }
}
