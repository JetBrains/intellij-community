open class <caret>Base

class Child : Base()

fun check(value: Any) = value is Base
fun cast(value: Any) = value as Base
fun literal() = Base::class
