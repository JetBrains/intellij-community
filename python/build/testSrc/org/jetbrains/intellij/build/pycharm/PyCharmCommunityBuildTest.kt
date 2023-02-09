// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import com.intellij.openapi.application.PathManager
import com.intellij.util.io.Compressor
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.testFramework.runTestBuild
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

class PyCharmCommunityBuildTest {
  companion object {
    fun stubSkeletons(homePath: Path, options: BuildOptions) {
      val root = homePath.resolve("skeletons")
      for (name in listOf("skeletons-win-stub.zip", "skeletons-mac-stub.zip")) {
        val path = root.resolve(name)
        synchronized("$path".intern()) {
          if (path.exists()) return@synchronized
          println("Creating ${path.relativeTo(homePath)} test stub for TeamCity artifact dependency")
          path.parent.createDirectories()
          when (path.extension) {
            "zip" -> Compressor.Zip(path).use {
              val content = Files.createTempFile(path.nameWithoutExtension, "txt")
              it.addFile(content.name, content, TimeUnit.SECONDS.toMillis(options.buildDateInSeconds))
            }
            else -> path.createFile()
          }
        }
      }
    }
  }

  @Test
  fun testBuild() {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    val communityHomePath = BuildDependenciesCommunityRoot(homePath.resolve("community"))
    runTestBuild(
      homePath = communityHomePath.communityRoot,
      communityHomePath = communityHomePath,
      productProperties = PyCharmCommunityProperties(communityHomePath.communityRoot),
    ) {
      it.classesOutputDirectory = System.getProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY)
                                  ?: "$homePath/out/classes"
      stubSkeletons(communityHomePath.communityRoot, it)
    }
  }
}