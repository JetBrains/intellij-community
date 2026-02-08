package com.intellij.ide.starter.di

import com.intellij.ide.starter.buildTool.BuildTool
import com.intellij.ide.starter.buildTool.GradleBuildTool
import com.intellij.ide.starter.buildTool.JpsBuildTool
import com.intellij.ide.starter.buildTool.MavenBuildTool
import com.intellij.ide.starter.frameworks.AndroidFramework
import com.intellij.ide.starter.frameworks.Framework
import com.intellij.ide.starter.frameworks.SpringFramework
import com.intellij.ide.starter.ide.IDETestContext
import org.kodein.di.DI
import org.kodein.di.factory
import org.kodein.di.inSet

val ideaFrameworksDI by DI.Module {
  inSet<Framework> {
    factory { testContext: IDETestContext -> SpringFramework(testContext) }
  }
  inSet<Framework> {
    factory { testContext: IDETestContext -> AndroidFramework(testContext) }
  }
}

val ideaBuildToolsDI by DI.Module {
  inSet<BuildTool> {
    factory { testContext: IDETestContext -> GradleBuildTool(testContext) }
  }
  inSet<BuildTool> {
    factory { testContext: IDETestContext -> MavenBuildTool(testContext) }
  }
  inSet<BuildTool> {
    factory { testContext: IDETestContext -> JpsBuildTool(testContext) }
  }
}