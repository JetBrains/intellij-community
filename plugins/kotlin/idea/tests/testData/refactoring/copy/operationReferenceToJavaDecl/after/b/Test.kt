package b

import a.Foo

fun test(): Boolean {
    return unresolvedReturnType() == unresolvedReturnType()
}

fun unresolvedReturnType(): Foo = Foo()