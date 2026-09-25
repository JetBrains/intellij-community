// NEW_NAME: execute
open class Task {
    open fun run() {}
}

class Chore : Task() {
    override fun run<caret>() {}
}
