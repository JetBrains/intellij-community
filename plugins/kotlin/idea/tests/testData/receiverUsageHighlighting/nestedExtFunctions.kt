class X {
    val length = 2
}

fun <info descr="null">~X</info>.foo() {
    <info descr="null">this</info>.length
    <info descr="null">length</info>
    <info descr="null">this</info>()

    fun X.bar() {
        this.length
        length
        this()
        <info descr="null">this</info>@foo.length
        this@bar.length
    }
}

operator fun X.invoke() {}
public inline fun <T, R> T.run(block: T.() -> R): R = block()