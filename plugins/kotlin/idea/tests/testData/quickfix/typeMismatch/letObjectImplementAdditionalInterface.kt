// "Let the anonymous object implement interface 'A'" "true"
package let.implement

fun bar() {
    foo(<caret>object {})
}

fun foo(a: A) {
}

interface A
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix