// "Let 'C' implement interface 'I'" "true"

class C {
    fun exampleMethod() {
    }
}

interface I {
    fun exampleMethod()
}

fun acceptExampleInterface(i: I) {
    i.exampleMethod()
}

fun testCase(c: C) {
    acceptExampleInterface(<caret>c)
}

// IGNORE_K1
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.LetImplementInterfaceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.LetImplementInterfaceFixFactories$LetImplementInterfaceFix