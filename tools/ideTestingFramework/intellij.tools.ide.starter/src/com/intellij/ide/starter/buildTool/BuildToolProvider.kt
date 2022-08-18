package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext

abstract class BuildToolProvider(val testContext: IDETestContext) {
  abstract val maven: MavenBuildTool

  abstract val gradle: GradleBuildTool
}