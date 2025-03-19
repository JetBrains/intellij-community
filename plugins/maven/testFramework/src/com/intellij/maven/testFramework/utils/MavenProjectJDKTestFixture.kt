// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.UsefulTestCase.assertNotNull
import com.intellij.testFramework.fixtures.IdeaTestFixture
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import java.io.File

class MavenProjectJDKTestFixture(val project: Project, val jdkName: String) : IdeaTestFixture {
  private lateinit var myJdkHome: String

  @RequiresWriteLock
  override fun setUp() {
    myJdkHome = IdeaTestUtil.requireRealJdkHome()
    val oldJdk = ProjectJdkTable.getInstance().findJdk(jdkName)
    if (oldJdk != null) {
      ProjectJdkTable.getInstance().removeJdk(oldJdk)
    }
    val jdkHomeDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(myJdkHome))
    val jdk = SdkConfigurationUtil.setupSdk(arrayOfNulls<Sdk>(0), jdkHomeDir!!, JavaSdk.getInstance(), true, null, jdkName)
    assertNotNull("Cannot create JDK for $myJdkHome", jdk)
    ProjectJdkTable.getInstance().addJdk(jdk!!)
    val projectRootManager = ProjectRootManager.getInstance(project)
    if (projectRootManager.getProjectSdk() == null) {
      projectRootManager.setProjectSdk(jdk)
    }
  }

  @RequiresWriteLock
  override fun tearDown() {
    if (!this::myJdkHome.isInitialized) {
      //super.setUp() wasn't called
      return
    }
    val jdk = ProjectJdkTable.getInstance().findJdk(jdkName)
    if (jdk != null) {
      ProjectJdkTable.getInstance().removeJdk(jdk)
    }
  }
}