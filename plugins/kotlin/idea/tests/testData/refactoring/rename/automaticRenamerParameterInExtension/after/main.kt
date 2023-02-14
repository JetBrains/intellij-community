package testing

interface Interface {
    open fun Int.foo(a: Int, b: String) {
    }
}

open class Super {
    open fun Int.foo(a: Int, b: String) {
    }
}

open class Middle : Super(), Interface {
    override fun Int.foo(aa: Int, b: String) {
    }
}

class Sub : Middle() {
    override fun Int.foo(aa: Int, b: String) {
    }
}
