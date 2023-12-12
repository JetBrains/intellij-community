// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.ProjectWizardTestCase
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard
import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.server.MavenServerManager
import java.nio.file.Path

abstract class MavenProjectWizardTestCase : ProjectWizardTestCase<AbstractProjectWizard>() {
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
}