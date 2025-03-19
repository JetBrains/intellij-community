class X {
    val length = 2
}

fun foo() {
    X().run {
        <info descr="null">~this</info>
        <info descr="null">this</info>.length
        <info descr="null">length</info>
        <info descr="null">this</info>@run.length
        <info descr="null">this</info>()

        <info descr="null">run</info> {
            length // no highlighting. The receiver is from `run` here.
        }
    }
}

operator fun X.invoke() {}
public inline fun <T, R> T.run(block: T.() -> R): R = block()