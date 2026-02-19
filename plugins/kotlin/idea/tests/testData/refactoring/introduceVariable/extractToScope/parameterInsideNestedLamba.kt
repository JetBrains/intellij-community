fun foo(f: (Int) -> Int) = f(0)

fun test() {
    foo { p -> foo { <selection>p</selection> } }
}