// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.frameActivationCache

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.IdeFrame
import java.util.concurrent.atomic.AtomicLong

@Service(Service.Level.APP)
internal class FrameActivationCounter : Disposable {
  private val counterImpl: AtomicLong = AtomicLong(Long.MIN_VALUE)

  companion object {
    val counter: Long get() = ApplicationManager.getApplication().service<FrameActivationCounter>().counterImpl.get()
  }

  init {
    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe(ApplicationActivationListener.TOPIC, object : ApplicationActivationListener {
        override fun applicationActivated(ideFrame: IdeFrame) {
          counterImpl.incrementAndGet()
        }
      })
  }

  override fun dispose(): Unit = Unit
}

internal class CacheHolderImpl<T : Any> (val counter: Long, val value: T)
