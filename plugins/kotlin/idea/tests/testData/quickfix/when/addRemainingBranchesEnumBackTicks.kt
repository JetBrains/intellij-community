// "Add remaining branches" "true"
// WITH_STDLIB

enum class FooEnum {
    A, B, `C`, `true`, `false`, `null`
}

fun test(foo: FooEnum?) = <caret>when (foo) {
    FooEnum.A -> "A"
}
/* IGNORE_FIR */
