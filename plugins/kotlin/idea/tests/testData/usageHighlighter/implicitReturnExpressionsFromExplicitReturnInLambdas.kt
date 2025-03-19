fun some(a: Int, b: Int) {
    run <info descr="null">{</info>
        val i = 12
        val j = 13
        if (a > 50) {
            if (b > 100) {
                <info descr="null">i + j</info>
            } else {
                <info descr="null">i * j</info>
            }
        } else {
            <info descr="null">~return@run false</info>
        }
    <info descr="null">}</info>
}

fun <T> run(a: () -> T) {}