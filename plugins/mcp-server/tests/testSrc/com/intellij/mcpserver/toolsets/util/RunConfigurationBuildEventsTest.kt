// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.toolsets.util

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildProgressListenerRegistrar
import com.intellij.build.BuildProgressObservable
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.containers.DisposableWrapperList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class RunConfigurationBuildEventsTest {
  private val project by projectFixture()

  @Test
  fun `collects events of a registered observable until the run disposes the listener`() {
    val observable = TestBuildProgressObservable()
    BuildProgressListenerRegistrar.registerBuildProgressListeners(project, observable)
    val errors = RunConfigurationBuildErrors { 42L }
    val disposable = Disposer.newDisposable()
    try {
      errors.listen(project, disposable)
      observable.emit(42L, StartBuildEvent.builder("Build", DefaultBuildDescriptor(42L, "Build", "/project", 0)).build())
      observable.emit(42L, MessageEvent.builder("Compiler executable not found", MessageEvent.Kind.ERROR).build())
      assertEquals("Compiler executable not found", errors.getErrorText())
    }
    finally {
      Disposer.dispose(disposable)
    }

    observable.emit(42L, MessageEvent.builder("Late failure", MessageEvent.Kind.ERROR).build())
    assertEquals("Compiler executable not found", errors.getErrorText())
  }
}

private class TestBuildProgressObservable : BuildProgressObservable {
  private val listeners = DisposableWrapperList<BuildProgressListener>()

  override fun addListener(listener: BuildProgressListener, disposable: Disposable) {
    listeners.add(listener, disposable)
  }

  fun emit(buildId: Any, event: BuildEvent) {
    for (listener in listeners) {
      listener.onEvent(buildId, event)
    }
  }
}
