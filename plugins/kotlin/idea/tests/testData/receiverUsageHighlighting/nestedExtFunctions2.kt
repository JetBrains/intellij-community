class X {
    val length = 2
}

fun X.foo() {
    this.length
    length
    this()

    fun <info descr="null">~X</info>.bar() {
        <info descr="null">this</info>.length
        <info descr="null">length</info>
        <info descr="null">this</info>()
        this@foo.length
        <info descr="null">this</info>@bar.length
    }
}

operator fun X.invoke() {}
public inline fun <T, R> T.run(block: T.() -> R): R = block()