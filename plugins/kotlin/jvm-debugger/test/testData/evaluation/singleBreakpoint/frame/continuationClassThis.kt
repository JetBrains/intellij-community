package continuationClassThis

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun main() {
    Handler().handle()
}

class Handler : Continuation<Unit> {
    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    override fun resumeWith(result: Result<Unit>) {}

    fun handle() {
        //Breakpoint!
        val x = 1
    }
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
