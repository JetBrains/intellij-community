@file:JvmName("StateFlowMerge")
package flows.stateFlow.merge

// ATTACH_LIBRARY: maven(com.intellij.platform:kotlinx-coroutines-core-jvm:1.8.0-intellij-11)

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

val f1 = `~~~!!! recognizableFrameWithEmitInAsyncStackTraceConstructor 1 !!!~~~`(-2)
val f2 = `~~~!!! recognizableFrameWithEmitInAsyncStackTraceConstructor 2 !!!~~~`(-1)
val f3 = `~~~!!! recognizableFrameWithEmitInAsyncStackTraceConstructor 3 !!!~~~`(0)

fun main(): Unit = runBlocking {
    val job = launch {
        val f = listOf(f1, f2, f3).asFlow().flattenMerge()
        f.collect {
            //Breakpoint!
            println(it)
        }
    }

    launch {
        delay(50)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign !!!~~~`(f1, 1)
        delay(50)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit !!!~~~`(f2, 2)
        delay(50)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTraceCas !!!~~~`(f3, 0, 3)
        delay(50)
        job.cancel()
    }
}

fun `~~~!!! recognizableFrameWithEmitInAsyncStackTraceConstructor 1 !!!~~~`(value: Int): MutableStateFlow<Int> {
    return MutableStateFlow(value)
}

fun `~~~!!! recognizableFrameWithEmitInAsyncStackTraceConstructor 2 !!!~~~`(value: Int): MutableStateFlow<Int> {
    return MutableStateFlow(value)
}

fun `~~~!!! recognizableFrameWithEmitInAsyncStackTraceConstructor 3 !!!~~~`(value: Int): MutableStateFlow<Int> {
    return MutableStateFlow(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceAssign !!!~~~`(flow: MutableStateFlow<T>, value: T) {
    flow.value = value
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceEmit !!!~~~`(flow: MutableStateFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTraceCas !!!~~~`(flow: MutableStateFlow<T>, old: T, new: T) {
    flow.compareAndSet(old, new)
}
