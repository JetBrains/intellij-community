fun intVararg(valuesArePresent: Boolean, vararg values: Int) = acceptAnyVararg(valuesArePresent, values)

fun acceptAnyVararg(valuesArePresent: Boolean, vararg values: Any) {
}

fun test() {
    int<caret>Vararg(false)
    intVararg(true, 1)
    intVararg(true, 1, 2)
}
