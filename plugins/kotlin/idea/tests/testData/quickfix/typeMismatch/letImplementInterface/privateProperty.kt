// "Let 'A' implement interface 'IA'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'A', but 'IA' was expected.

interface IA {
    var a: Int
    fun iaAction()
}

class B {
    fun doAction(ia: IA) {}
}

class A {
    private var a: Int = 0

    fun action() {
    }
}

fun testCase(a: A) {
    B().doAction(<caret>a)
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.LetImplementInterfaceFixFactories$LetImplementInterfaceFix
