// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.IdeaTestFixture
import com.intellij.testFramework.replaceService
import org.jetbrains.idea.maven.server.MavenIndexerWrapper
import org.jetbrains.idea.maven.server.MavenServerConnector
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.Assert.fail
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Predicate

class RealMavenPreventionFixture(val project: Project) : IdeaTestFixture {
  private lateinit var disposable: Disposable
  private lateinit var manager: NoRealMavenServerManager
  override fun setUp() {
    disposable = Disposer.newDisposable("Real maven protector for MavenSyncTest")
    manager = NoRealMavenServerManager()
    ApplicationManager.getApplication().replaceService(MavenServerManager::class.java, manager, disposable)
  }

  override fun tearDown() {
    if (::disposable.isInitialized) {
      Disposer.dispose(disposable)
    }
    if (::manager.isInitialized) {
      if (manager.stacktraces.isNotEmpty()) {
        val result = manager.stacktraces.map {
          it.stackTraceToString()
        }
        fail("No Real Maven should be accessed, but it was. Here is the list of stacktraces: \n" +
             result.joinToString("\n"))
      }
    }
  }
}

private class NoRealMavenServerManager : MavenServerManager {
  val stacktraces = CopyOnWriteArrayList<Exception>()
  override fun dispose() {
  }

  override fun getAllConnectors(): MutableCollection<MavenServerConnector> {
    noRealMavenAllowed()
  }

  override fun shutdownMavenConnectors(project: Project, condition: Predicate<MavenServerConnector>) {
    noRealMavenAllowed()
  }

  override fun getConnectorBlocking(project: Project, workingDirectory: String): MavenServerConnector {
    noRealMavenAllowed()
  }

  override suspend fun getConnector(project: Project, workingDirectory: String, jdk: Sdk): MavenServerConnector {
    noRealMavenAllowed()
  }

  override fun shutdownConnector(connector: MavenServerConnector, wait: Boolean): Boolean {
    noRealMavenAllowed()
  }

  override fun closeAllConnectorsAndWait() {
    noRealMavenAllowed()
  }

  override fun getMavenEventListener(): File {
    noRealMavenAllowed()
  }

  override fun getMavenEventListenerPath(): Path {
    noRealMavenAllowed()
  }

  override fun createIndexer(): MavenIndexerWrapper {
    noRealMavenAllowed()
  }

  private fun noRealMavenAllowed(): Nothing {
    val exception = IllegalStateException("No real maven permitted when RealMavenPreventionFixture is enabled")
    stacktraces.add(exception)
    throw exception
  }
}


