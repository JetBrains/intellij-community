// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext

abstract class BuildToolProvider(val testContext: IDETestContext) {
  abstract val maven: MavenBuildTool

  abstract val gradle: GradleBuildTool
}