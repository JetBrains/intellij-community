fun <info descr="null">String</info>.foo() {
    print(<info descr="null">this</info>)
    print(<info descr="null">this</info>.length)
    print(<info descr="null">length</info>)
    print(<info descr="null">this</info>@foo.length)
    <info descr="null">~this</info>()

    <info descr="null">run</info> {
        print(length) // no highlighting. The receiver is from `run` here.
    }
}

operator fun String.invoke() {}
public inline fun <T, R> T.run(block: T.() -> R): R = block()