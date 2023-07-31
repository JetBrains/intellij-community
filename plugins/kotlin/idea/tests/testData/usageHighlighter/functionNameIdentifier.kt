<info descr="null">fun</info>~ test(s: String?): Int {
    if (s != null) {
        <info descr="null">return@<info descr="null">test</info> 1</info>
    }
    <info descr="null">return 0</info>
}
val q = <info descr="null">test</info>("")
