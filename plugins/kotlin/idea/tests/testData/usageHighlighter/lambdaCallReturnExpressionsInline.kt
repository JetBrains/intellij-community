fun some(a: Int, b: Int) {
    <info descr="null">~run</info> {
        val i = 12
        val j = 13
        if (a > 50) {
            if (b > 100) {
                <info descr="null">i + j</info>
            } else {
                return@some
            }
        } else {
            <info descr="null">return@run false</info>
        }
    }
}

inline fun <T> run(a: () -> T) {
}