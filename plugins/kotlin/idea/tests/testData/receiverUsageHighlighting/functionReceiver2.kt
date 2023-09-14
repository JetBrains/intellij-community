class X {
    operator fun invoke() {}
}

fun <info descr="null">~X</info>.foo() {
    <info descr="null">this</info>()

    <info descr="null">run</info> {
        print(this()) // no highlighting. The receiver is from `run` here.
    }
}

public inline fun <T, R> T.run(block: T.() -> R): R = block()