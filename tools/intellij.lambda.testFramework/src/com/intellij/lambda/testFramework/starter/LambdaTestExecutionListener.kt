package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer
import com.intellij.ide.starter.coroutine.CommonScope
import com.intellij.ide.starter.di.di
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestPlan
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.net.URI

class LambdaTestExecutionListener : TestExecutionListener {
  companion object {
    init {
      CommonScope.perSuiteScopeForIdeActivities()

      di = DI {
        extend(di)

        bindSingleton(tag = "teamcity.uri", overrides = true) { URI("https://buildserver.labs.intellij.net").normalize() }
        bindSingleton<CIServer>(overrides = true) { TeamCityCIServer() }
      }

    }
  }

  override fun testPlanExecutionFinished(testPlan: TestPlan) {
    IdeInstance.stopIde()
  }
}
