// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.ProjectWizardTestCase
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard
import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.maven.testFramework.assertWithinTimeout
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenSyncListener
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenLog
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

abstract class MavenProjectWizardTestCase : ProjectWizardTestCase<AbstractProjectWizard>() {
  override fun runInDispatchThread() = false

  override fun tearDown() = runBlocking {
    try {
      MavenServerManager.getInstance().shutdown(true)
      withContext(Dispatchers.EDT) {
        JavaAwareProjectJdkTableImpl.removeInternalJdkInTests()
      }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  protected fun createPom(): Path {
    return createPom("pom.xml")
  }

  protected fun createPom(pomName: String): Path {
    return createTempFile(pomName, MavenTestCase.createPomXml(
      """
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      """.trimIndent())).toPath()
  }

  protected fun createMavenWrapper(pomPath: Path, context: String) {
    val fileName = pomPath.parent.resolve(".mvn").resolve("wrapper").resolve("maven-wrapper.properties")
    fileName.write(context)
  }

  protected suspend fun importProjectFrom(path: Path): Module {
    return waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        importProjectFrom(path.toString(), null, MavenProjectImportProvider())
      }
    }
  }

  protected suspend fun importModuleFrom(path: Path): Module {
    return waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        importModuleFrom(MavenProjectImportProvider(), path.toString())
      }
    }
  }

  @RequiresBackgroundThread
  protected suspend fun <T> waitForImportWithinTimeout(action: suspend () -> T): T {
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
      assertTrue(
        syncStarted.get()
        && syncFinished.get()
      )
      MavenLog.LOG.warn("waitForImportWithinTimeout finished")
    }

    return result
  }

}