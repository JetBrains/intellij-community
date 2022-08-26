package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.utils.catchAll
import com.intellij.ide.starter.utils.logOutput
import com.intellij.util.io.exists
import java.util.*

class TestWatcherActions {
  private val _onFailureActions: MutableList<(IDETestContext) -> Unit> = Collections.synchronizedList(mutableListOf())
  val onFailureActions: List<(IDETestContext) -> Unit>
    get() = synchronized(_onFailureActions) { _onFailureActions.toList() }

  private val _onFinishedActions: MutableList<(IDETestContext) -> Unit> = Collections.synchronizedList(mutableListOf())
  val onFinishedActions: List<(IDETestContext) -> Unit>
    get() = synchronized(_onFinishedActions) { _onFinishedActions.toList() }

  companion object {
    /** Archive and add to test artifact entire ide's `system` dir */
    fun getSystemDirAsArtifactAction(): (IDETestContext) -> Unit = { testContext ->
      catchAll {
        logOutput("Archive with system directory created and will be published to artifacts")
        testContext.publishArtifact(source = testContext.paths.systemDir, artifactName = "testSystemDirSnapshot.zip")

        val ideaDirPath = testContext.resolvedProjectHome.resolve(".idea")

        if (ideaDirPath.exists()) {
          logOutput("Archive with .idea dir created and will be published to artifacts")
          testContext.publishArtifact(source = ideaDirPath, artifactName = ".idea.zip")
        }
      }
    }
  }

  fun addOnFailureAction(action: (IDETestContext) -> Unit): TestWatcherActions {
    synchronized(_onFailureActions) { _onFailureActions.add(action) }
    return this
  }

  fun addOnFinishedAction(action: (IDETestContext) -> Unit): TestWatcherActions {
    synchronized(_onFinishedActions) { _onFinishedActions.add(action) }
    return this
  }
}