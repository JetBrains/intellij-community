class <info descr="null">Foo</info>(val foo: Int) // the declaration

fun bar() {
    <info descr="null">Foo</info>(1)
    ::<info descr="null">~Foo</info>
    <info descr="null">Foo</info>::class
}