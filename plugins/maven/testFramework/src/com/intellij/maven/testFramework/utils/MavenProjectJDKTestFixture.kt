// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.testFramework.eelJava.EelTestJdkProvider
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixture
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.junit.jupiter.api.fail
import java.nio.file.Path

class MavenProjectJDKTestFixture(val project: Project, val jdkName: String) : IdeaTestFixture, Disposable {
  private lateinit var jdk: Sdk

  @RequiresWriteLock
  override fun setUp() {
    val myJdkHome = getJdkHome()
    VfsRootAccess.allowRootAccess(this, myJdkHome.toString())
    val oldJdk = ProjectJdkTable.getInstance(project).findJdk(jdkName)
    if (oldJdk != null) {
      ProjectJdkTable.getInstance(project).removeJdk(oldJdk)
    }
    val jdkHomeDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(myJdkHome)
                     ?: fail { "Cannot find JDK home: $myJdkHome" }
    jdk = SdkConfigurationUtil.setupSdk(arrayOfNulls<Sdk>(0), jdkHomeDir, JavaSdk.getInstance(), true, null, jdkName)
          ?: fail { "Cannot create JDK for $myJdkHome" }
    if (jdk.name != jdkName) {
      LOG.warn("Created JDK name '${jdk.name}' differs from expected '$jdkName'")
    }
    ProjectJdkTable.getInstance(project).addJdk(jdk)
    val projectRootManager = ProjectRootManager.getInstance(project)
    if (projectRootManager.getProjectSdk() == null) {
      projectRootManager.setProjectSdk(jdk)
    }
  }

  @RequiresWriteLock
  override fun tearDown(): Unit = runAll(
    { removeJdkFromJdkTable() },
    { Disposer.dispose(this) }
  )

  private fun removeJdkFromJdkTable() {
    if (!this::jdk.isInitialized) {
      // either super.setUp() wasn't called or jdk creation failed
      return
    }
    ProjectJdkTable.getInstance(project).removeJdk(jdk)
  }

  override fun dispose(): Unit = Unit

  private fun getJdkHome(): Path {
    val eelDescriptor = project.getEelDescriptor()
    val eelJdkPath = EelTestJdkProvider.getJdkPath(eelDescriptor)
    if (eelJdkPath != null) {
      return eelJdkPath
    }
    val jdkHome = IdeaTestUtil.requireRealJdkHome()
    return Path.of(jdkHome)
  }

  companion object {
    private val LOG = logger<MavenProjectJDKTestFixture>()
  }
}