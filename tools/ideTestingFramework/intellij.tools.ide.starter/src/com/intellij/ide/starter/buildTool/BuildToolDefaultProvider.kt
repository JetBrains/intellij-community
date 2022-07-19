// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext

open class BuildToolDefaultProvider(testContext: IDETestContext) : BuildToolProvider(testContext) {
  override val maven: MavenBuildTool = MavenBuildTool(testContext)

  override val gradle: GradleBuildTool = GradleBuildTool(testContext)
}