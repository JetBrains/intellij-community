// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.testFramework.runTestBuild
import org.junit.Test

class PyCharmCommunityBuildTest {
  @Test
  fun testBuild() {
    val homePath = PathManager.getHomePathFor(javaClass)!!
    val communityHomePath = "$homePath/community"
    runTestBuild(
      homePath = communityHomePath,
      communityHomePath = communityHomePath,
      productProperties = PyCharmCommunityProperties(communityHomePath),
    ) {
      it.projectClassesOutputDirectory = System.getProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY)
                                         ?: "$homePath/out/classes"
    }
  }
}