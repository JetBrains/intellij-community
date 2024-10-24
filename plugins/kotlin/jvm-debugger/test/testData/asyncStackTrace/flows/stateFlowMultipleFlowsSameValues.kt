@file:JvmName("StateFlowMultipleFlowsSameValues.kt")
package flows.stateFlow.stateFlowMultipleFlowsSameValues.kt

// ATTACH_LIBRARY: maven(com.intellij.platform:kotlinx-coroutines-core-jvm:1.8.0-intellij-11)

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

val stateFlow1 = `~~~!!! recognizableFrameWithEmitInAsyncStackTraceConstructor 1 !!!~~~`()
val stateFlow2 = `~~~!!! recognizableFrameWithEmitInAsyncStackTraceConstructor 2 !!!~~~`()

fun main(): Unit = runBlocking {
    val job1 = launch {
        stateFlow1.collect {
            //Breakpoint!
            println(it)
        }
    }
    val job2 = launch {
        stateFlow2.collect {
            //Breakpoint!
            println(it)
        }
    }

    launch {
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 1 !!!~~~`(stateFlow1, 42)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 2 !!!~~~`(stateFlow2, 42)
        delay(50)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 1 !!!~~~`(stateFlow1, 81)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 2 !!!~~~`(stateFlow2, 81)
        delay(50)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceCas 1 !!!~~~`(stateFlow1, 81, 42)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceCas 2 !!!~~~`(stateFlow2, 81, 42)
        delay(50)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 3 !!!~~~`(stateFlow1, 81)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 4 !!!~~~`(stateFlow2, 81)
        delay(50)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 3 !!!~~~`(stateFlow1, 42)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 4 !!!~~~`(stateFlow2, 42)
        delay(50)
        job1.cancel()
        job2.cancel()
    }
}

fun `~~~!!! recognizableFrameWithEmitInAsyncStackTraceConstructor 1 !!!~~~`(): MutableStateFlow<Int> {
    return MutableStateFlow(-1)
}

fun `~~~!!! recognizableFrameWithEmitInAsyncStackTraceConstructor 2 !!!~~~`(): MutableStateFlow<Int> {
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

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign 4 !!!~~~`(flow: MutableStateFlow<T>, value: T) {
    flow.value = value
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit 4 !!!~~~`(flow: MutableStateFlow<T>, value: T) {
    flow.emit(value)
}
