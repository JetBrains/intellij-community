// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PythonConsoleClientUtil")

package com.jetbrains.python.console

import com.intellij.openapi.application.ApplicationManager
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock

@JvmOverloads
fun synchronizedPythonConsoleClient(loader: ClassLoader,
                                    delegate: PythonConsoleBackendService.Iface,
                                    pythonConsoleProcess: Process? = null): PythonConsoleBackendService.Iface {
  val lock = ReentrantLock()
  return Proxy.newProxyInstance(loader, arrayOf<Class<*>>(PythonConsoleBackendService.Iface::class.java),
                                InvocationHandler { _, method, args ->
                                  val future = ApplicationManager.getApplication().executeOnPooledThread(Callable<Any> {
                                    lock.lock()
                                    try {
                                      if (args == null) {
                                        return@Callable method.invoke(delegate)
                                      }
                                      else {
                                        return@Callable method.invoke(delegate, *args)
                                      }
                                    }
                                    finally {
                                      lock.unlock()
                                    }
                                  })

                                  if (pythonConsoleProcess == null) {
                                    return@InvocationHandler future.get()
                                  }

                                  while (true) {
                                    try {
                                      return@InvocationHandler future.get(10L, TimeUnit.MILLISECONDS)
                                    }
                                    catch (e: TimeoutException) {
                                      if (!pythonConsoleProcess.isAlive) {
                                        val exitValue = pythonConsoleProcess.exitValue()
                                        throw RuntimeException(
                                          "Console already exited with value: $exitValue while waiting for an answer.\n")
                                      }
                                    }
                                  }
                                }) as PythonConsoleBackendService.Iface
}
