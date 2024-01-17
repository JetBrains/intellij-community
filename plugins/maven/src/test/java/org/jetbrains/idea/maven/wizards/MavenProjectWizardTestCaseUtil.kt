// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.ProjectWizardTestCase
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard
import com.intellij.maven.testFramework.assertWithinTimeout
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.idea.maven.project.MavenSyncListener
import org.jetbrains.idea.maven.utils.MavenLog
import java.util.concurrent.atomic.AtomicBoolean

@RequiresBackgroundThread
internal suspend fun <T, W : AbstractProjectWizard?> ProjectWizardTestCase<W>.waitForImportWithinTimeout(action: suspend () -> T): T {
  MavenLog.LOG.warn("waitForImportWithinTimeout started")
  val syncStarted = AtomicBoolean(false)
  val syncFinished = AtomicBoolean(false)
  ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
    .subscribe(MavenSyncListener.TOPIC, object : MavenSyncListener {
      override fun syncStarted(project: Project) {
        syncStarted.set(true)
      }

      override fun syncFinished(project: Project) {
        syncFinished.set(true)
      }
    })

  val result = action()

  assertWithinTimeout {
    ProjectWizardTestCase.assertTrue(
      syncStarted.get()
      && syncFinished.get()
    )
    MavenLog.LOG.warn("waitForImportWithinTimeout finished")
  }

  return result
}