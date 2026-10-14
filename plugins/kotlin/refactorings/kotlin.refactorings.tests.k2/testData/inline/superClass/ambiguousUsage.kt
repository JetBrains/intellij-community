// ERROR: A reference to the superclass is ambiguous because the superclass has multiple inheritors.
open class <caret>Base
class Child : Base()
class Other : Base()
fun use(value: Base) = value
