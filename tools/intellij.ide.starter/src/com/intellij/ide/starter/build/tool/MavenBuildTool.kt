package com.intellij.ide.starter.build.tool

import com.intellij.ide.starter.ide.IDETestContext
import java.nio.file.Path

class MavenBuildTool(testContext: IDETestContext) : BuildTool(BuildToolType.MAVEN, testContext) {
  val localMavenRepo: Path
    get() = testContext.paths.tempDir.resolve(".m3").resolve("repository")

  fun useNewMavenLocalRepository(): MavenBuildTool {
    localMavenRepo.toFile().mkdirs()
    testContext.addVMOptionsPatch { addSystemProperty("idea.force.m2.home", localMavenRepo.toString()) }
    return this
  }
}