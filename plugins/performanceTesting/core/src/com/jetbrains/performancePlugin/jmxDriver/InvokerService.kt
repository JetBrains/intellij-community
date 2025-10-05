// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.jmxDriver

import com.intellij.driver.impl.Invoker
import com.intellij.driver.impl.InvokerMBean
import com.intellij.driver.model.RdTarget
import com.intellij.driver.model.transport.Ref
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.util.PlatformUtils
import io.opentelemetry.context.Context
import java.awt.Component
import java.lang.management.ManagementFactory
import java.util.function.Supplier
import javax.management.JMException
import javax.management.ObjectName

@Service(Service.Level.APP)
class InvokerService {
  companion object {
    @JvmStatic
    fun getInstance(): InvokerService = ApplicationManager.getApplication().getService(InvokerService::class.java)

    private const val BACKEND_JMX_PORT_PROPERTY = "rdct.tests.backendJmxPort"
    private const val BACKEND_JMX_HOST_PROPERTY = "rdct.tests.backendJmxHost"
    private val jmxBackendPort = System.getProperty(BACKEND_JMX_PORT_PROPERTY)
    private val jmxBackendHost = System.getProperty(BACKEND_JMX_HOST_PROPERTY, "127.0.0.1")
    private val log = Logger.getInstance(InvokerService::class.java)
  }

  private var myInvoker: InvokerMBean? = null

  val rdTarget: RdTarget = when {
    PlatformUtils.isJetBrainsClient() -> RdTarget.FRONTEND
    AppMode.isRemoteDevHost() -> RdTarget.BACKEND
    else -> RdTarget.DEFAULT
  }

  val invoker: InvokerMBean
    get() = myInvoker ?: throw IllegalStateException("Invoker is not registered")

  fun isReady(): Boolean = myInvoker != null

  fun putReference(c: Component): Ref {
    return invoker.putAdhocReference(c)
  }

  @Throws(JMException::class)
  fun register(tracerSupplier: Supplier<out IJTracer?>,
               timedContextSupplier: Supplier<out Context?>,
               screenshotAction: (String) -> String?) {
    val objectName = ObjectName("com.intellij.driver:type=Invoker")
    val server = ManagementFactory.getPlatformMBeanServer()

    val localInvoker = Invoker(rdTarget, tracerSupplier, timedContextSupplier, screenshotAction)

    val remoteJmxAddress = jmxBackendPort?.let { "$jmxBackendHost:$it" }

    if (PlatformUtils.isJetBrainsClient() && remoteJmxAddress != null) {
      log.info("Remote Dev Mode, $remoteJmxAddress")
      myInvoker = RemoteDevInvoker(localInvoker, remoteJmxAddress)
    } else {
      myInvoker = localInvoker
    }
    server.registerMBean(invoker, objectName)
  }
}