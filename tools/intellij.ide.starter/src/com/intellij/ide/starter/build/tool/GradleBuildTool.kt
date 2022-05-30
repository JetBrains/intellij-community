package com.intellij.ide.starter.build.tool

import com.intellij.ide.starter.ide.IDETestContext
import java.nio.file.Path

class GradleBuildTool(testContext: IDETestContext) : BuildTool(BuildToolType.GRADLE, testContext) {
  val localGradleRepo: Path
    get() = testContext.paths.tempDir.resolve("gradle")

  fun useNewGradleLocalCache(): GradleBuildTool {
    localGradleRepo.toFile().mkdirs()
    testContext.addVMOptionsPatch { addSystemProperty("gradle.user.home", localGradleRepo.toString()) }
    return this
  }
}