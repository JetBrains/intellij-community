// DISABLE_ERRORS
fun foo(x: (Int) -> Int) { }

fun test() {
    foo(<selection>{ it }</selection>)
    foo({ x -> x })
    foo({ x: UnresolvedType -> x })
}
