package continuation
// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.3.8)-javaagent
// SHOW_LIBRARY_STACK_FRAMES: false
// REGISTRY: debugger.library.frames.fold.instead.of.hide=true
// REGISTRY: debugger.async.stacks.coroutines=false

import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        //Breakpoint!
        println("inside coroutine")
    }
}
