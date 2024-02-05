package com.jetbrains.performancePlugin.jmxDriver

import com.intellij.driver.impl.Invoker
import com.intellij.driver.impl.InvokerMBean
import com.intellij.openapi.components.Service
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.util.PlatformUtils
import io.opentelemetry.context.Context
import java.lang.management.ManagementFactory
import java.util.function.Consumer
import java.util.function.Supplier
import javax.management.JMException
import javax.management.ObjectName
import com.intellij.openapi.diagnostic.Logger;


@Service(Service.Level.APP)
class InvokerService {
  companion object {
    const val RD_HOST_REMOTE_JMX_ADDRESS_PROPERTY = "remote.driver.rd.host.remoteJmxAddress"
    private val log = Logger.getInstance(InvokerService::class.java)
  }
  var invoker: InvokerMBean? = null
    private set

  fun isReady(): Boolean = invoker != null

  @Throws(JMException::class)
  fun register(tracerSupplier: Supplier<out IJTracer?>,
               timedContextSupplier: Supplier<out Context?>,
               screenshotAction: Consumer<String?>) {
    val objectName = ObjectName("com.intellij.driver:type=Invoker")
    val server = ManagementFactory.getPlatformMBeanServer()

    val localInvoker = Invoker("v", tracerSupplier, timedContextSupplier, screenshotAction)

    val remoteJmxAddress = System.getProperty(RD_HOST_REMOTE_JMX_ADDRESS_PROPERTY)

    if (PlatformUtils.isJetBrainsClient() && remoteJmxAddress != null) {
      log.info("Remote Dev Mode")
      invoker = RemoteDevInvoker(localInvoker, remoteJmxAddress)
    } else {
      invoker = localInvoker
    }
    server.registerMBean(invoker, objectName)
  }
}