// "Surround with arrayOf(...)" "true"

class Foo<T>(vararg val p: T)

fun test() {
    Foo(p = 123<caret>)
}