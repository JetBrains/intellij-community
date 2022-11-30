// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.testFramework.runTestBuild
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.relativeTo

class PyCharmCommunityBuildTest {
  companion object {
    fun stubSkeletons(homePath: Path) {
      val root = homePath.resolve("skeletons")
      for (name in listOf("skeletons-win-stub.zip", "skeletons-mac-stub.zip")) {
        val path = root.resolve(name)
        println("Creating ${path.relativeTo(homePath)} test stub for TeamCity artifact dependency")
        Files.createDirectories(path.parent)
        if (Files.notExists(path)) Files.createFile(path)
      }
    }
  }

  @Test
  fun testBuild() {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    val communityHomePath = BuildDependenciesCommunityRoot(homePath.resolve("community"))
    stubSkeletons(communityHomePath.communityRoot)
    runTestBuild(
      homePath = communityHomePath.communityRoot,
      communityHomePath = communityHomePath,
      productProperties = PyCharmCommunityProperties(communityHomePath.communityRoot),
    ) {
      it.classesOutputDirectory = System.getProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY)
                                  ?: "$homePath/out/classes"
    }
  }
}