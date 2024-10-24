@file:JvmName("StateFlowDebounce")
package flows.stateFlow.debounce

// ATTACH_LIBRARY: maven(com.intellij.platform:kotlinx-coroutines-core-jvm:1.8.0-intellij-11)

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

val stateFlow = `~~~!!! recognizableFrameWithEmitInAsyncStackTraceConstructor !!!~~~`()

fun main(): Unit = runBlocking {
    val job = launch {
        stateFlow.debounce(75).collect {
            //Breakpoint!
            println(it)
        }
    }

    launch {
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 1 !!!~~~`(stateFlow, 0)
        delay(50)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 1 !!!~~~`(stateFlow, 1)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceCas 1 !!!~~~`(stateFlow, 1, 2)
        delay(50)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 2 !!!~~~`(stateFlow, 3)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 2 !!!~~~`(stateFlow, 4)
        delay(50)
        job.cancel()
    }
}

fun `~~~!!! recognizableFrameWithEmitInAsyncStackTraceConstructor !!!~~~`(): MutableStateFlow<Int> {
    return MutableStateFlow(-1)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 1 !!!~~~`(flow: MutableStateFlow<T>, value: T) {
    flow.value = value
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 1 !!!~~~`(flow: MutableStateFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceCas 1 !!!~~~`(flow: MutableStateFlow<T>, old: T, new: T) {
    flow.compareAndSet(old, new)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 2 !!!~~~`(flow: MutableStateFlow<T>, value: T) {
    flow.value = value
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 2 !!!~~~`(flow: MutableStateFlow<T>, value: T) {
    flow.emit(value)
}

