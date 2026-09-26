class Foo {
    companion object {
        fun bar(str: String) = str.toInt() + str.toInt()
    }
}

fun test() {
    "foo".let(Foo::bar<caret>)
}