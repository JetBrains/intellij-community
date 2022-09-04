package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.utils.logOutput
import java.nio.file.Path

open class MavenBuildTool(testContext: IDETestContext) : BuildTool(BuildToolType.MAVEN, testContext) {
  val localMavenRepo: Path
    get() = testContext.paths.tempDir.resolve(".m3").resolve("repository")

  fun useNewMavenLocalRepository(): MavenBuildTool {
    localMavenRepo.toFile().mkdirs()
    testContext.addVMOptionsPatch { addSystemProperty("idea.force.m2.home", localMavenRepo.toString()) }
    return this
  }

  fun removeMavenConfigFiles(): MavenBuildTool {
    logOutput("Removing Maven config files in ${testContext.resolvedProjectHome} ...")

    testContext.resolvedProjectHome.toFile().walkTopDown()
      .forEach {
        if (it.isFile && it.name == "pom.xml") {
          it.delete()
          logOutput("File ${it.path} is deleted")
        }
      }

    return this
  }
}