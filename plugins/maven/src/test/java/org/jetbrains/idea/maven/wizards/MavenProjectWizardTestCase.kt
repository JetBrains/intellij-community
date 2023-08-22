// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.ProjectWizardTestCase
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard
import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.idea.maven.project.importing.MavenImportingManager.Companion.getInstance
import org.jetbrains.idea.maven.server.MavenServerManager
import java.nio.file.Path

open class MavenProjectWizardTestCase : ProjectWizardTestCase<AbstractProjectWizard>() {
  override fun tearDown() {
    try {
      if (getInstance(myProject).isImportingInProgress()) {
        PlatformTestUtil.waitForPromise(getInstance(myProject).getImportFinishPromise())
      }
      if (createdProject != null && getInstance(createdProject).isImportingInProgress()) {
        PlatformTestUtil.waitForPromise(getInstance(createdProject).getImportFinishPromise())
      }
      MavenServerManager.getInstance().shutdown(true)
      JavaAwareProjectJdkTableImpl.removeInternalJdkInTests()
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