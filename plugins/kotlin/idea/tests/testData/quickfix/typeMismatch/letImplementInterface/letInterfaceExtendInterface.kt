// "Let 'C' extend interface 'A'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'C', but 'A' was expected.
package let.extend

fun bar() {
    foo(B() as C<caret>)
}


fun foo(a: A) {
}

interface A
interface C
class B : C
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.LetImplementInterfaceFixFactories$LetImplementInterfaceFix