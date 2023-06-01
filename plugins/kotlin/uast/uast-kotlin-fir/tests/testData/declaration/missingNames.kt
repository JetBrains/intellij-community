// KTIJ-25335
class SimpleClass(valOne: String, valTwo: Int) {
    val
}

class Foo(val name: String, val)

// KTIJ-23584
interface Foo {
    val
}

// KTIJ-24121
class MyClass<T>(val value: T) {
    fun getValAsString(): T {
        return value.toString()
    }
    val
}
