class A {
    fun foo() {
        invokeLater { println("a") }
    }

    companion object {
        fun invokeLater(doRun: Runnable?) {
        }
    }
}
