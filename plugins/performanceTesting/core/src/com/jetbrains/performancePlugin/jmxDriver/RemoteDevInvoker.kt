// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.jmxDriver

import com.intellij.driver.client.Driver
import com.intellij.driver.client.impl.DriverImpl
import com.intellij.driver.client.impl.Invoker
import com.intellij.driver.client.impl.JmxHost
import com.intellij.driver.impl.InvokerMBean
import com.intellij.driver.model.ProductVersion
import com.intellij.driver.model.RdTarget
import com.intellij.driver.model.transport.Ref
import com.intellij.driver.model.transport.RemoteCall
import com.intellij.driver.model.transport.RemoteCallResult


interface RemoteDevInvokerMBean : InvokerMBean {
  val driver: Driver
}

internal class RemoteDevInvoker(private val localInvoker: InvokerMBean, remoteJmxAddress: String) : RemoteDevInvokerMBean {

  override val driver: DriverImpl

  init {
    val originalClassLoader = Thread.currentThread().getContextClassLoader()
    try {
      Thread.currentThread().setContextClassLoader(this::class.java.getClassLoader())
      driver = Driver.create(JmxHost(null, null, remoteJmxAddress)) as DriverImpl
    }
    finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader)
    }
  }

  private val remoteInvoker: Invoker
    get() = driver.getInvoker()

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
    when (call.rdTarget) {
      RdTarget.FRONTEND -> return localInvoker.invoke(call)
      RdTarget.BACKEND -> return invokeRemote(call)
      RdTarget.DEFAULT -> throw IllegalArgumentException("RdTarget should be resolved on the caller side")
    }
  }

  private fun invokeRemote(call: RemoteCall): RemoteCallResult {
    val originalClassLoader = Thread.currentThread().getContextClassLoader()
    try {
      Thread.currentThread().setContextClassLoader(this::class.java.getClassLoader())
      return remoteInvoker.invoke(call)
    }
    finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader)
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