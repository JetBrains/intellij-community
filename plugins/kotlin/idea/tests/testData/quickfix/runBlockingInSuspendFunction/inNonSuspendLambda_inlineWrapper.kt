// "Replace 'runBlocking' with inline code" "false"
// WITH_COROUTINES
package test

import kotlinx.coroutines.runBlocking

fun customFunction(action: () -> Unit) {
    action()
}

suspend fun main() {
    customFunction {
        run {
            <caret>runBlocking {
                println("Hello")
            }
        }
    }
}
