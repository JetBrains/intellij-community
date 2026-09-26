fun main() {
    "foo".<caret>let(Foo::myToInt)
}

class Foo {
    companion object {
        fun myToInt(str: String) = str.toInt()
    }
}