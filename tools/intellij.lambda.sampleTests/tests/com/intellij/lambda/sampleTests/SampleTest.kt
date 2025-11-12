// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.sampleTests

import com.intellij.ide.plugins.PluginModuleDescriptor
import com.intellij.lambda.testFramework.junit.ExecuteInMonolithAndSplitMode
import com.intellij.lambda.testFramework.junit.UltimateTestCases.JpsEmptyProject
import com.intellij.lambda.testFramework.testApi.editor.openFile
import com.intellij.lambda.testFramework.testApi.getProject
import com.intellij.lambda.testFramework.testApi.getProjects
import com.intellij.lambda.testFramework.testApi.utils.tryTimes
import com.intellij.lambda.testFramework.testApi.waitForProject
import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.currentClassLogger
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.remoteDev.tests.LambdaFrontendContext
import com.intellij.remoteDev.tests.impl.LambdaTestHost.Companion.NamedLambda
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdKeyValueEntry
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.TestTemplate
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

@ExecuteInMonolithAndSplitMode
class SampleTest {
  @TestTemplate
  fun `serialized test`(ide: BackgroundRunWithLambda) = runBlocking {
    //works in both modes if
    // headless is turned off for monolith in com.intellij.lambda.testFramework.starter.NewContextWithLambdaKt.newContextWithLambda
    // as ProjectManager returns empty projects list in headless
    JpsEmptyProject.projectInfo.projectDir.resolve("src").resolve("FormattingExamplesExpected.java").let {
      if (!it.exists()) {
        it.parent.createDirectories()
        it.createFile()
      }
    }
    ide.apply {
      runInBackend {
        waitForProject(20.seconds)
      }

      run {
        Logger.getInstance("test").warn("Projects: " + getProjects().joinToString { it.name })
      }

      runInBackend {
        Logger.getInstance("test").warn("backend Projects: " + getProject())
        openFile("src/FormattingExamplesExpected.java", waitForReadyState = false, requireFocus = false)
      }
    }
    Unit
  }

  @TestTemplate
  fun `named lambda test`(ide: BackgroundRunWithLambda) = runBlocking {
    ide.runNamedLambdaInBackend(HelloBackendOnlyLambda::class)
    ide.runNamedLambda(HelloFrontendOnlyLambda::class)
  }

  companion object {
    class HelloFrontendOnlyLambda(frontendIdeContext: LambdaFrontendContext, plugin: PluginModuleDescriptor)
      : NamedLambda<LambdaFrontendContext>(frontendIdeContext, plugin) {
      override suspend fun LambdaFrontendContext.lambda(args: List<LambdaRdKeyValueEntry>): Any {
        return tryTimes(2, "Log") { currentClassLogger().warn("Hi there Frontend") }
      }
    }

    class HelloBackendOnlyLambda(backendIdeContext: LambdaBackendContext, plugin: PluginModuleDescriptor)
      : NamedLambda<LambdaBackendContext>(backendIdeContext, plugin) {
      override suspend fun LambdaBackendContext.lambda(args: List<LambdaRdKeyValueEntry>): Any {
        return tryTimes(2, "Log") { currentClassLogger().warn("Hi there Backend") }

      }
    }
  }
}

