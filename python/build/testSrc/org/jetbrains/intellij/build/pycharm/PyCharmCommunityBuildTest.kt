// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import com.intellij.openapi.application.PathManager
import com.intellij.platform.buildScripts.testFramework.runEssentialPluginsTest
import com.intellij.platform.buildScripts.testFramework.runTestBuild
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

class PyCharmCommunityBuildTest {
  @Test
  fun build(testInfo: TestInfo) {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    val communityHomePath = BuildDependenciesCommunityRoot(homePath.resolve("community"))
    runTestBuild(communityHomePath.communityRoot, PyCharmCommunityProperties(communityHomePath.communityRoot), testInfo) {
      it.classOutDir = System.getProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY) ?: "${homePath}/out/classes"
    }
  }

  @Test
  fun `essential plugins depend only on essential plugins`() {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    runEssentialPluginsTest(homePath, PyCharmCommunityProperties(homePath), ProprietaryBuildTools.DUMMY)
  }
}
