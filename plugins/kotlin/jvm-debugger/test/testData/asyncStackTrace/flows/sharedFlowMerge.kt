@file:JvmName("SharedFlowMerge")
package flows.sharedFlow.merge

// ATTACH_LIBRARY: maven(com.intellij.platform:kotlinx-coroutines-core-jvm:1.8.0-intellij-11)

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

val f1 = MutableSharedFlow<Int>(replay = 1)
val f2 = MutableSharedFlow<Int>()
val f3 = MutableSharedFlow<Int>()

fun main(): Unit = runBlocking {
    val job = launch {
        val f = listOf(f1, f2, f3).asFlow().flattenMerge()
        f.collect {
            //Breakpoint!
            println(it)
        }
    }

    launch {
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 1 !!!~~~`(f1, 1)
        delay(10)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 2 !!!~~~`(f2, 2)
        delay(10)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 3 !!!~~~`(f3, 3)
        delay(10)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 4 !!!~~~`(f1, 4)
        delay(10)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 5 !!!~~~`(f2, 5)
        delay(10)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 6 !!!~~~`(f3, 6)
        delay(10)
        job.cancel()
    }
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 1 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 2 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 3 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 4 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 5 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 6 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}
