class A {
    fun foo() {
        invokeLater(object : Runnable {
            override fun run() {
                println("a")
            }
        })
    }

    companion object {
        fun invokeLater(doRun: Runnable?) {
        }
    }
}
