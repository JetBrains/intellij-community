package com.jetbrains.performancePlugin.jmxDriver

import com.intellij.driver.client.impl.Invoker
import com.intellij.driver.client.impl.JmxCallHandler
import com.intellij.driver.client.impl.JmxHost
import com.intellij.driver.impl.InvokerMBean
import com.intellij.driver.model.ProductVersion
import com.intellij.driver.model.transport.Ref
import com.intellij.driver.model.transport.RemoteCall
import com.intellij.driver.model.transport.RemoteCallResult
import kotlin.jvm.java


interface RemoteDevInvokerMBean : InvokerMBean

internal class RemoteDevInvoker(private val localInvoker: InvokerMBean, remoteJmxAddress: String) : RemoteDevInvokerMBean {
  private val remoteInvoker = JmxCallHandler.jmx(Invoker::class.java, JmxHost(null, null, remoteJmxAddress))

  override fun getProductVersion(): ProductVersion {
    return localInvoker.productVersion
  }

  override fun isApplicationInitialized(): Boolean {
    return localInvoker.isApplicationInitialized
  }

  override fun exit() {
    return localInvoker.exit()
  }

  override fun invoke(call: RemoteCall): RemoteCallResult {

    try {
      return localInvoker.invoke(call)
    }
    // replace with ReferenceNotFoundException later
    catch (e: Throwable) {
      return remoteInvoker.invoke(call)
    }
  }

  override fun newSession(): Int {
    val sessionId = localInvoker.newSession()
    remoteInvoker.newSession(sessionId)
    return sessionId
  }

  override fun newSession(id: Int): Int {
    return localInvoker.newSession(id)
  }

  override fun cleanup(sessionId: Int) {
    localInvoker.cleanup(sessionId)
    remoteInvoker.cleanup(sessionId)
  }

  override fun takeScreenshot(outFolder: String?): String? {
    return localInvoker.takeScreenshot(outFolder)
  }

  override fun putAdhocReference(item: Any): Ref {
    return localInvoker.putAdhocReference(item)
  }
}