// NEW_NAME: execute
open class Runner {
    open fun run() {}
}

class Worker : Runner() {
    override fun run<caret>() {}
}
