// "Let 'A' implement interface 'IB'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'A', but 'IB' was expected.

interface IA<T> {
    fun foo(t: T)
}

interface IB: IA<Int>

fun doAction(ia: IB) {}

class A {
    fun foo(t: Int) {
    }
}

fun testCase(a: A) {
    doAction(<caret>a)
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.LetImplementInterfaceFixFactories$LetImplementInterfaceFix
