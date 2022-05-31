package com.intellij.ide.starter.build.tool

import com.intellij.ide.starter.ide.IDETestContext

open class BuildToolDefaultProvider(testContext: IDETestContext) : BuildToolProvider(testContext) {
  override val maven: MavenBuildTool = MavenBuildTool(testContext)

  override val gradle: GradleBuildTool = GradleBuildTool(testContext)
}