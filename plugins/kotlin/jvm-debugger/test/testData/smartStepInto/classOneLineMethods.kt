class Clazz {
    fun bar() = <caret>a()
    fun baz() = b()
    fun a() = 42
    fun b() = 42
}

// EXISTS: a()
// IGNORE_K2
