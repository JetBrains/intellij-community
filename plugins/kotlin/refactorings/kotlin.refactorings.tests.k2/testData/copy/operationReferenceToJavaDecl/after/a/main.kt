package a

fun test(): Boolean {
    return unresolvedReturnType() == unresolvedReturnType()
}

fun unresolvedReturnType(): Foo = Foo()