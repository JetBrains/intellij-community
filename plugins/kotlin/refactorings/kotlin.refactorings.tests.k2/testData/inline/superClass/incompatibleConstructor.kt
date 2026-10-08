// ERROR: A call to the superclass constructor cannot call the subclass constructor with the same arguments.
open class <caret>Base
class Child(val value: Int) : Base()
fun create() = Base()
