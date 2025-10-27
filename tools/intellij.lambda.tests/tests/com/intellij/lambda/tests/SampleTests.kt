package com.intellij.lambda.tests

import com.intellij.ide.plugins.PluginModuleDescriptor
import com.intellij.ide.starter.junit5.RemoteDevRun
import com.intellij.lambda.testFramework.junit.MonolithAndSplitModeIdeInstanceInitializer
import com.intellij.lambda.testFramework.testApi.editor.openFile
import com.intellij.lambda.testFramework.testApi.getProject
import com.intellij.lambda.testFramework.testApi.getProjects
import com.intellij.lambda.testFramework.testApi.waitForProject
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.currentClassLogger
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.remoteDev.tests.LambdaFrontendContext
import com.intellij.remoteDev.tests.impl.LambdaTestHost.Companion.NamedLambda
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdKeyValueEntry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Duration.Companion.seconds

@ExtendWith(RemoteDevRun::class, MonolithAndSplitModeIdeInstanceInitializer::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SampleTests {
  @Test
  fun `serialized test`() {
    //works in both modes if
    // TestCases.IU.JavaTestProject is used and
    // headless is turned off for monolith
    runBlocking {
      MonolithAndSplitModeIdeInstanceInitializer.ideBackgroundRun.apply {
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
    }
  }

  @Test
  fun `named lambda test`() {
    runBlocking {
      MonolithAndSplitModeIdeInstanceInitializer.ideBackgroundRun.apply {
        runLambdaInBackend(HelloBackendOnlyLambda::class)

        runLambda(HelloFrontendOnlyLambda::class)
      }
    }
  }

  companion object {
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
  }
}

