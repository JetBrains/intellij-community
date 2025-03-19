@file:JvmName("StateFlowComples")
package flows.stateFlow.complex

// ATTACH_LIBRARY: maven(com.intellij.platform:kotlinx-coroutines-core-jvm:1.8.0-intellij-11)

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

val stateFlow = `~~~!!! recognizableFrameWithEmitInAsyncStackTraceConstructor !!!~~~`()

fun main(): Unit = runBlocking {
    val job = launch {
        stateFlow
            .map { it * 2 }
            .filter { it % 2 == 0 }
            .scan(0) { accumulator, value -> accumulator + value }
            .buffer(2)
            .collect {
                //Breakpoint!
                println(it)
            }
    }

    launch {
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 1 !!!~~~`(stateFlow, 0)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 1 !!!~~~`(stateFlow, 1)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceCas 1 !!!~~~`(stateFlow, 1, 2)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 2 !!!~~~`(stateFlow, 3)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 2 !!!~~~`(stateFlow, 4)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 3 !!!~~~`(stateFlow, 5)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 3 !!!~~~`(stateFlow, 6)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceCas 3 !!!~~~`(stateFlow, 6, 7)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 4 !!!~~~`(stateFlow, 8)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 4 !!!~~~`(stateFlow, 9)
        delay(100)
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

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceCas 2 !!!~~~`(flow: MutableStateFlow<T>, old: T, new: T) {
    flow.compareAndSet(old, new)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 3 !!!~~~`(flow: MutableStateFlow<T>, value: T) {
    flow.value = value
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 3 !!!~~~`(flow: MutableStateFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceCas 3 !!!~~~`(flow: MutableStateFlow<T>, old: T, new: T) {
    flow.compareAndSet(old, new)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 4 !!!~~~`(flow: MutableStateFlow<T>, value: T) {
    flow.value = value
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 4 !!!~~~`(flow: MutableStateFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceCas 4 !!!~~~`(flow: MutableStateFlow<T>, old: T, new: T) {
    flow.compareAndSet(old, new)
}
