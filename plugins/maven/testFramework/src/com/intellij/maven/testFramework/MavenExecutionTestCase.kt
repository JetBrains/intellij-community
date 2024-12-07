// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.ThrowableRunnable
import java.io.File

abstract class MavenExecutionTestCase : MavenMultiVersionImportingTestCase() {
  private var myJdkHome: String? = null

  public override fun setUp() {
    edt<RuntimeException?>(ThrowableRunnable {
      myJdkHome = IdeaTestUtil.requireRealJdkHome()
      VfsRootAccess.allowRootAccess(getTestRootDisposable(), myJdkHome!!)
      super.setUp()
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable {
        val oldJdk = ProjectJdkTable.getInstance().findJdk(JDK_NAME)
        if (oldJdk != null) {
          ProjectJdkTable.getInstance().removeJdk(oldJdk)
        }
        val jdkHomeDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(myJdkHome!!))
        val jdk = SdkConfigurationUtil.setupSdk(arrayOfNulls<Sdk>(0), jdkHomeDir!!, JavaSdk.getInstance(), true, null, JDK_NAME)
        assertNotNull("Cannot create JDK for $myJdkHome", jdk)
        ProjectJdkTable.getInstance().addJdk(jdk!!)
        val projectRootManager = ProjectRootManager.getInstance(project)
        if (projectRootManager.getProjectSdk() == null) {
          projectRootManager.setProjectSdk(jdk)
        }
      })
    })
  }

  public override fun tearDown() {
    edt<RuntimeException?>(ThrowableRunnable {
      if (myJdkHome == null) {
        //super.setUp() wasn't called
        return@ThrowableRunnable
      }
      val jdk = ProjectJdkTable.getInstance().findJdk(JDK_NAME)
      if (jdk != null) {
        WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { ProjectJdkTable.getInstance().removeJdk(jdk) })
      }
      super.tearDown()
    })
  }

  companion object {
    private const val JDK_NAME = "MavenExecutionTestJDK"
  }
}
