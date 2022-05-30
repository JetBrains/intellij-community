package com.intellij.ide.starter.build.tool

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import org.kodein.di.direct
import org.kodein.di.factory
import org.kodein.di.newInstance

class BuildToolResolver(val testContext: IDETestContext) {
  val maven: MavenBuildTool
    get() = di.direct.newInstance {
      factory<IDETestContext, MavenBuildTool>().invoke(testContext)
    }

  val gradle: GradleBuildTool
    get() = di.direct.newInstance {
      factory<IDETestContext, GradleBuildTool>().invoke(testContext)
    }
}