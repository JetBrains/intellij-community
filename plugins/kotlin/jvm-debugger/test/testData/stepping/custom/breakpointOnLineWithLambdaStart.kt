// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.6.4.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.6.4.jar)
package breakpointOnLineWithLambdaStart

import kotlinx.coroutines.*


fun main() = runBlocking {
    //Breakpoint!, lambdaOrdinal = -1
    launch {
        println("In launch")
    }.join()

    //Breakpoint!, lambdaOrdinal = -1
    async {
        println("In async")
    }.await()

    //Breakpoint!, lambdaOrdinal = -1
    withContext(Dispatchers.IO) {
        println("In withContext")
    }

    //Breakpoint!, lambdaOrdinal = -1
    foo {
        println("In foo")
    }

    //Breakpoint!, lambdaOrdinal = -1
    fooParam { x ->
        println("In fooParam $x")
    }

    //Breakpoint!, lambdaOrdinal = -1
    suspendFoo {
        println("In suspendFoo")
    }

    //Breakpoint!, lambdaOrdinal = -1
    suspendFooParam { x ->
        println("In suspendFooParam $x")
    }

    //Breakpoint!, lambdaOrdinal = -1
    inlineFoo {
        println("In inlineFoo")
    }
}

fun foo(lambda: () -> Unit) {
    lambda()
}

fun fooParam(lambda: (Int) -> Unit) {
    lambda(42)
}

suspend fun suspendFoo(lambda: () -> Unit) {
    lambda()
}

suspend fun suspendFooParam(lambda: (Int) -> Unit) {
    lambda(42)
}

inline fun inlineFoo(lambda: () -> Unit) {
    lambda()
}


// RESUME: 100
