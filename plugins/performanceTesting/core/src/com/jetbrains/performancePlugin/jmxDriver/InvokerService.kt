package com.jetbrains.performancePlugin.jmxDriver

import com.intellij.driver.impl.Invoker
import com.intellij.driver.impl.InvokerMBean
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.util.PlatformUtils
import io.opentelemetry.context.Context
import java.lang.management.ManagementFactory
import java.util.function.Consumer
import java.util.function.Supplier
import javax.management.JMException
import javax.management.ObjectName


@Service(Service.Level.APP)
class InvokerService {
  companion object {
    @JvmStatic
    fun getInstance(): InvokerService = ApplicationManager.getApplication().getService<InvokerService>(InvokerService::class.java)

    private const val BACKEND_JMX_PORT_PROPERTY = "rdct.tests.backendJmxPort"
    private const val JMX_BACKEND_IP = "127.0.0.1"
    private val jmxBackendPort = System.getProperty(BACKEND_JMX_PORT_PROPERTY)
    private val log = Logger.getInstance(InvokerService::class.java)
  }
  var invoker: InvokerMBean? = null
    private set

  fun isReady(): Boolean = invoker != null

  @Throws(JMException::class)
  fun register(tracerSupplier: Supplier<out IJTracer?>,
               timedContextSupplier: Supplier<out Context?>,
               screenshotAction: (String) -> String) {
    val objectName = ObjectName("com.intellij.driver:type=Invoker")
    val server = ManagementFactory.getPlatformMBeanServer()

    val localInvoker = Invoker("v", tracerSupplier, timedContextSupplier, screenshotAction)

    val remoteJmxAddress = jmxBackendPort?.let { "$JMX_BACKEND_IP:$it" }

    if (PlatformUtils.isJetBrainsClient() && remoteJmxAddress != null) {
      log.info("Remote Dev Mode")
      invoker = RemoteDevInvoker(localInvoker, remoteJmxAddress)
    } else {
      invoker = localInvoker
    }
    server.registerMBean(invoker, objectName)
  }
}