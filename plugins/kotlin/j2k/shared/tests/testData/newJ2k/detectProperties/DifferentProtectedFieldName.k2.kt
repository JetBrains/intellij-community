open class AAA {
    var x: Int = 42
        protected set

    fun foo(other: AAA) {
        println(this.x)
        println(other.x)
        this.x = 10
    }
}

internal class BBB : AAA() {
    fun bar() {
        println(this.x)
        this.x = 10
    }
}
