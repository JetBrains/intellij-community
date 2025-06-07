fun foo() {
    "".apply {
        fun String.tagText1(): Foo = TODO()
        val <caret>a = tagText1() // Inline property
        a()
    }
}

class Foo {
    operator fun invoke(): Unit = TODO()
}

// IGNORE_K1