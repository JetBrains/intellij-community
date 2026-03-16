// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.idea.maven.server.security.MavenToken
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import java.io.IOException
import java.rmi.server.UnicastRemoteObject
import java.util.UUID

abstract class MavenRemoteObjectWrapper<T> protected constructor() : RemoteObjectWrapper<T>() {
  private class RemoteMavenServerProgressIndicator(private val myProcess: MavenProgressIndicator) : MavenRemoteObject(), MavenServerProgressIndicator {
    override fun setText(text: @NlsContexts.ProgressText String?) {
      myProcess.setText(text)
    }

    override fun setText2(text: @NlsContexts.ProgressDetails String?) {
      myProcess.setText2(text)
    }

    override fun isCanceled(): Boolean {
      return myProcess.isCanceled
    }

    override fun setIndeterminate(value: Boolean) {
      myProcess.indicator.isIndeterminate = value
    }

    override fun setFraction(fraction: Double) {
      myProcess.setFraction(fraction)
    }
  }

  companion object {
    @JvmField
    val ourToken: MavenToken = MavenToken(UUID.randomUUID().toString())

    fun <Some : MavenRemoteObject?> doWrapAndExport(`object`: Some): Some? {
      try {
        val remote = UnicastRemoteObject
          .exportObject(`object`, 0)
        if (remote == null) {
          return null
        }
        return `object`
      }
      catch (e: IOException) {
        throw RuntimeException(e)
      }
    }

    fun wrapAndExport(indicator: MavenProgressIndicator): MavenServerProgressIndicator {
      return doWrapAndExport(RemoteMavenServerProgressIndicator(indicator))!!
    }
  }
}
