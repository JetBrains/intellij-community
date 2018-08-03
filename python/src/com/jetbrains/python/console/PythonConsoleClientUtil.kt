// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PythonConsoleClientUtil")

package com.jetbrains.python.console

import com.intellij.util.ConcurrencyUtil
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.*

private const val PYTHON_CONSOLE_COMMAND_THREAD_FACTORY_NAME: String = "Python Console Command Executor"

@JvmOverloads
fun synchronizedPythonConsoleClient(loader: ClassLoader,
                                    delegate: PythonConsoleBackendService.Iface,
                                    pythonConsoleProcess: Process? = null): PythonConsoleBackendService.Iface {
  val executorService = newSingleThreadPythonConsoleCommandExecutor()
  return Proxy.newProxyInstance(loader, arrayOf<Class<*>>(PythonConsoleBackendService.Iface::class.java),
                                InvocationHandler { _, method, args ->
                                  // we evaluate the original method in the other thread in order to control it
                                  val future = executorService.submit(Callable {
                                    return@Callable invokeOriginalMethod(args, method, delegate)
                                  })

                                  if (pythonConsoleProcess == null) {
                                    try {
                                      return@InvocationHandler future.get()
                                    }
                                    catch (e: ExecutionException) {
                                      throw e.cause ?: e
                                    }
                                  }

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
}

/**
 * Creates new single thread executor with [ThreadPoolExecutor.keepAliveTime]
 * equals to 60 sec.
 */
private fun newSingleThreadPythonConsoleCommandExecutor(): ExecutorService {
  val threadFactory = ConcurrencyUtil.newNamedThreadFactory(PYTHON_CONSOLE_COMMAND_THREAD_FACTORY_NAME)
  return ThreadPoolExecutor(0, 1, 60L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(), threadFactory)
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