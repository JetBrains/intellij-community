// "Let 'C' extend interface 'A'" "true"
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