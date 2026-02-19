package module1

import kotlinx.coroutines.*

private suspend fun a(): String {
    delay(1000)
    return "a"
}

fun computeA() = runBlocking {
    val res = async {
        a()
    }
    println(res.await())
}

fun main() {
    computeA()
}