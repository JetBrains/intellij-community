fun foo(f: (Int) -> Int) = f(0)

fun test() {
    foo {
        val p = 1
        foo { <selection>p</selection> }
    }
}