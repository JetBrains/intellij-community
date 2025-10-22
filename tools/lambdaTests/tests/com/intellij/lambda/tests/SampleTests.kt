package intellij.lambda.com.intellij.lambda.tests

import com.intellij.ide.plugins.PluginModuleDescriptor
import com.intellij.ide.starter.extended.allure.IjplComponents
import com.intellij.ide.starter.extended.remdev.RemoteDevRun
import com.intellij.lambda.testFramework.junit.MonolithAndSplitModeIdeInstanceInitializer
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

@IjplComponents.TODO
@ExtendWith(RemoteDevRun::class, MonolithAndSplitModeIdeInstanceInitializer::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SampleTests {
  @Test
  fun `serialized test`() {
    runBlocking {
      MonolithAndSplitModeIdeInstanceInitializer.ideBackgroundRun.apply {
        runSerializedLambda {
          Logger.getInstance("test").error("test message in frontend/monolith")
        }

        runSerializedLambdaInBackend {
          Logger.getInstance("test").error("test message in backend/monolith")
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

