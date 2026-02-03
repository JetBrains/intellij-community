// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

open class AAA {
    context(<caret>a: String)
    open fun abc(i: Int) {}
}

class BBB: AAA() {
    context(a: String)
    override fun abc(i: Int) {
        super.abc(i)
    }
}


// IGNORE_K1