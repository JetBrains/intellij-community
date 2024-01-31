package com.jetbrains.performancePlugin.jmxDriver

import com.intellij.driver.impl.Invoker
import com.intellij.driver.impl.InvokerMBean
import com.intellij.openapi.components.Service
import com.intellij.platform.diagnostic.telemetry.IJTracer
import io.opentelemetry.context.Context
import java.lang.management.ManagementFactory
import java.util.function.Consumer
import java.util.function.Supplier
import javax.management.JMException
import javax.management.ObjectName

@Service(Service.Level.APP)
class InvokerService {
  var invoker: InvokerMBean? = null
    private set

  fun isReady(): Boolean = invoker != null

  @Throws(JMException::class)
  fun register(tracerSupplier: Supplier<out IJTracer?>,
               timedContextSupplier: Supplier<out Context?>,
               screenshotAction: Consumer<String?>) {
    val objectName = ObjectName("com.intellij.driver:type=Invoker")
    val server = ManagementFactory.getPlatformMBeanServer()
    invoker = Invoker("v", tracerSupplier, timedContextSupplier, screenshotAction)
    server.registerMBean(invoker, objectName)
  }
}