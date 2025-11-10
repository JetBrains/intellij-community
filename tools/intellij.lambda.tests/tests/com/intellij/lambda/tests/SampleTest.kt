package com.intellij.lambda.tests

import com.intellij.ide.plugins.PluginModuleDescriptor
import com.intellij.lambda.testFramework.junit.ExecuteInMonolithAndSplitMode
import com.intellij.lambda.testFramework.testApi.editor.openFile
import com.intellij.lambda.testFramework.testApi.getProject
import com.intellij.lambda.testFramework.testApi.getProjects
import com.intellij.lambda.testFramework.testApi.waitForProject
import com.intellij.lambda.testFramework.utils.BackgroundRunWithLambda
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.currentClassLogger
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.remoteDev.tests.LambdaFrontendContext
import com.intellij.remoteDev.tests.impl.LambdaTestHost.Companion.NamedLambda
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdKeyValueEntry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.TestTemplate
import kotlin.time.Duration.Companion.seconds

@ExecuteInMonolithAndSplitMode
class SampleTest {
  @TestTemplate
  fun `serialized test`(ide: BackgroundRunWithLambda) = runBlocking {
    //works in both modes if
    // TestCases.IU.JavaTestProject is used and
    // headless is turned off for monolith
    ide.apply {
      runSerializedLambdaInBackend {
        waitForProject(20.seconds)
      }

      runSerializedLambda {
        Logger.getInstance("test").warn("Projects: " + getProjects().joinToString { it.name })
      }

      runSerializedLambdaInBackend {
        Logger.getInstance("test").warn("backend Projects: " + getProject())
        openFile("src/FormattingExamplesExpected.java", waitForReadyState = false)
      }
    }
    Unit
  }

  @TestTemplate
  fun `named lambda test`(ide: BackgroundRunWithLambda) = runBlocking {
    ide.runLambdaInBackend(HelloBackendOnlyLambda::class)
    ide.runLambda(HelloFrontendOnlyLambda::class)
  }
}

class HelloFrontendOnlyLambda(frontendIdeContext: LambdaFrontendContext, plugin: PluginModuleDescriptor)
  : NamedLambda<LambdaFrontendContext>(frontendIdeContext, plugin) {
  override suspend fun LambdaFrontendContext.lambda(args: List<LambdaRdKeyValueEntry>): Any {
    return currentClassLogger().warn("Hi there Frontend")
  }
}

class HelloBackendOnlyLambda(backendIdeContext: LambdaBackendContext, plugin: PluginModuleDescriptor)
  : NamedLambda<LambdaBackendContext>(backendIdeContext, plugin) {
  override suspend fun LambdaBackendContext.lambda(args: List<LambdaRdKeyValueEntry>): Any {
    return currentClassLogger().warn("Hi there Backend")
  }
}

