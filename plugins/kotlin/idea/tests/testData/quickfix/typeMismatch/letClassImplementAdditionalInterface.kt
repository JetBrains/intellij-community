// "Let 'B' implement interface 'A'" "true"
package let.implement

fun bar() {
    foo(B()<caret>)
}


fun foo(a: A) {
}

interface A
interface C
class B : C
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix
/* IGNORE_K2 */