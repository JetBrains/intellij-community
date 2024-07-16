// "Surround with null check" "true"

class Foo {
    fun foo(): Foo = Foo()
}

fun bar(f: () -> Foo) {}

fun test(x: Foo?) {
    bar {
        x<caret>.foo()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithNullCheckFixFactory$SurroundWithNullCheckFix