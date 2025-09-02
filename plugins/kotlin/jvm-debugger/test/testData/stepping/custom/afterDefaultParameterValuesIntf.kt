package test

fun main() {
    val foo = FooImpl()
    //Breakpoint!
    foo.call()
    foo.foo()
}

interface Foo {
    fun call(a: Int = def()) {
        foo()
    }

    fun def(): Int {
        return libFun() // 'Step over' causes debugger skipping call()
    }

    fun foo() {}
    fun libFun() = 12
}

class FooImpl : Foo

// STEP_INTO: 1
// STEP_OVER: 2

// JVM_DEFAULT_MODE: disable
// ^ After fixing IDEA-367937, please provide a copy of this test with the 'enable' mode.
