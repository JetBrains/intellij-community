package continuation
// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar)
// JVM_SYSTEM_PROPERTY: kotlinx.coroutines.debug.enable.creation.stack.trace=true
// REGISTRY: debugger.async.stacks.coroutines=false

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

fun main() {
    val mainTestVal = ""
    runBlocking {
        val rootCoroutineVal = mainTestVal
        TestSuspendA().a()
        print(rootCoroutineVal)
    }
}

class TestSuspendA {
    val classField = ""
    suspend fun a() {
        val methodVal = ""
        InClassB().b()
        print(methodVal)
    }

    class InClassB {
        val inClassBField = ""

        suspend fun b() {
            val inClassBMethodVal = ""
            InClassC().c()
            print(inClassBMethodVal)
        }

        inner class InClassC {
            val inClassCField = inClassBField

            suspend fun c() {
                yield()
                val inClassCMethodVal = ""
                //Breakpoint!
                print(inClassCMethodVal)
            }
        }
    }
}
