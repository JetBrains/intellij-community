fun main() {
    fun localFun() {
        <caret>Any().let {
            it
        }
    }
    localFun()
}

// EXISTS: constructor Any(), let: block.invoke()
// IGNORE_K2
