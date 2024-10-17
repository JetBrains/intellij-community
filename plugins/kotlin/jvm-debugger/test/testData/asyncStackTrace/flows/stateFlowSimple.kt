@file:JvmName("StateFlowSimple")
package flows.stateFlow.simple

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
        stateFlow.collect {
            //Breakpoint!
            println(it)
        }
    }

    launch {
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 1 !!!~~~`(stateFlow, 0)
        delay(10)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 1 !!!~~~`(stateFlow, 1)
        delay(10)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceCas 1 !!!~~~`(stateFlow, 1, 2)
        delay(10)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 2 !!!~~~`(stateFlow, 3)
        delay(10)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 2 !!!~~~`(stateFlow, 4)
        delay(10)
        job.cancel()
    }
}

fun `~~~!!! recognizableFrameWithEmitInAsyncStackTraceConstructor !!!~~~`(): MutableStateFlow<Int?> {
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
