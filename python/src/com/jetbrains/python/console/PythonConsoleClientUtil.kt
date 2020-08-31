// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PythonConsoleClientUtil")

package com.jetbrains.python.console

import com.intellij.util.ConcurrencyUtil
import com.jetbrains.python.console.protocol.PythonConsoleBackendService
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val PYTHON_CONSOLE_COMMAND_THREAD_FACTORY_NAME: String = "Python Console Command Executor"

fun synchronizedPythonConsoleClient(loader: ClassLoader,
                                    delegate: PythonConsoleBackendService.Iface,
                                    pythonConsoleProcess: Process): PythonConsoleBackendServiceDisposable {
  // DO NOT replace this ExecutorService with `SequentialTaskExecutor.createSequentialApplicationPoolExecutor()`!
  // It may use different threads for executing different tasks in a sequence and it breaks the prerequisite of `PipedInputStream`
  // that requires every read to be made from the same thread.
  val executorService = ConcurrencyUtil.newSingleThreadExecutor(PYTHON_CONSOLE_COMMAND_THREAD_FACTORY_NAME)

  val proxy = Proxy.newProxyInstance(loader, arrayOf<Class<*>>(
    PythonConsoleBackendService.Iface::class.java),
                                     InvocationHandler { _, method, args ->
                                       // we evaluate the original method in the other thread in order to control it
                                       val future = executorService.submit(Callable {
                                         return@Callable invokeOriginalMethod(args, method, delegate)
                                       })

                                       while (true) {
                                         try {
                                           return@InvocationHandler future.get(10L, TimeUnit.MILLISECONDS)
                                         }
                                         catch (e: TimeoutException) {
                                           if (!pythonConsoleProcess.isAlive) {
                                             val exitValue = pythonConsoleProcess.exitValue()
                                             throw PyConsoleProcessFinishedException(exitValue)
                                           }
                                           // continue waiting for the end of the operation execution
                                         }
                                         catch (e: ExecutionException) {
                                           if (!pythonConsoleProcess.isAlive) {
                                             val exitValue = pythonConsoleProcess.exitValue()
                                             throw PyConsoleProcessFinishedException(exitValue)
                                           }
                                           throw e.cause ?: e
                                         }
                                       }
                                     }) as PythonConsoleBackendService.Iface
  // make the `proxy` disposable
  return object : PythonConsoleBackendServiceDisposable, PythonConsoleBackendService.Iface by proxy {
    override fun dispose() {
      executorService.shutdownNow()
      try {
        while (!executorService.awaitTermination(1L, TimeUnit.SECONDS)) Unit
      }
      catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
      }
    }
  }
}

private fun invokeOriginalMethod(args: Array<out Any>?, method: Method, delegate: Any): Any? {
  return try {
    if (args != null) {
      method.invoke(delegate, *args)
    }
    else {
      method.invoke(delegate)
    }
  }
  catch (e: InvocationTargetException) {
    throw e.cause ?: e
  }
}