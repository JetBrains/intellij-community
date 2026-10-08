fun id(v: Int) = v
open class <caret>Base(value: Int) {
    val age = value
}

class Child : Base(id(1)) {
    val value = 1
}

fun result(child: Child) = child.value
