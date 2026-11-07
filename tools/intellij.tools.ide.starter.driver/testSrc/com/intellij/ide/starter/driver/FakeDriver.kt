package com.intellij.ide.starter.driver

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.DriverTestLogger
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * A [Driver] without an IDE. It writes the step log to [lines], and records the method names of the other utility calls,
 * for example of the overlay, in [overlayCalls]. Without [hasOverlay], those calls fail.
 */
internal class FakeDriver(isConnected: Boolean = true, failingLogger: Boolean = false, private val hasOverlay: Boolean = true) {
  val lines: MutableList<String> = CopyOnWriteArrayList()
  val overlayCalls: MutableList<String> = CopyOnWriteArrayList()

  private val logger = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(DriverTestLogger::class.java)) { _, method, args ->
    if (failingLogger) error("The IDE is gone")
    lines.add("${method.name}: ${args.single()}")
    null
  } as DriverTestLogger

  val proxy: Driver = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Driver::class.java)) { self, method, args ->
    @Suppress("UNCHECKED_CAST")
    when (method.name) {
      "isConnected" -> isConnected
      "utility" -> utility(args.first() as KClass<*>)
      "withContext" -> (args[2] as Driver.() -> Any?)(self as Driver)
      "hashCode" -> System.identityHashCode(self)
      "equals" -> self === args.single()
      "toString" -> "FakeDriver"
      else -> error("Unexpected call: ${method.name}")
    }
  } as Driver

  private fun utility(clazz: KClass<*>): Any =
    if (clazz == DriverTestLogger::class) logger
    else Proxy.newProxyInstance(javaClass.classLoader, arrayOf(clazz.java)) { _, method, _ ->
      overlayCalls.add(method.name)
      if (!hasOverlay) error("This IDE has no overlay")
      null
    }
}
