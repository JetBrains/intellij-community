// "Surround with arrayOf(...)" "true"
// K2_ERROR: Argument type mismatch: actual type is 'A', but 'Array<out A>' was expected.
// K2_ERROR: Assigning single elements to varargs in named form is prohibited.

package foo.bar

class A

fun foo(vararg a: A) {}

fun test() {
    foo(a = <caret>foo.bar.A())
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory$SurroundWithArrayModCommandAction