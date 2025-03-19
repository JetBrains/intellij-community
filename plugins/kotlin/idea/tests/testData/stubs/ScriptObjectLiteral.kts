fun execute(block: Runnable) {
    block()
}

execute(object : Runnable {
    override fun run() {
        val x = 0
    }
})