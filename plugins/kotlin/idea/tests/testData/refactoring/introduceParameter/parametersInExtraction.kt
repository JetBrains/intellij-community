fun interface ABCD {
    fun foo(a: Int, b: String)
}

fun abc(text1: String, text2: String) {
    unknown(
        text1,
        text2,
        <selection>ABCD { a, b ->
            // doSomething
        }</selection>
    )
}