class AAA {
    var x: Int = 42

    fun foo() {
        this.x = this.x + 1
    }

    fun bar(other: AAA) {
        other.x = other.x + 1
    }
}

internal class B {
    fun foo(a: AAA) {
        a.x = a.x + 1
    }
}
