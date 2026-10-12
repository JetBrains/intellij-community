// ERROR: A reference to the superclass is ambiguous because the superclass has multiple inheritors.
open class <caret>Base {
    inner class Reader
}

class Child : Base()
class Other : Base()

fun use(reader: Base.Reader) = reader
