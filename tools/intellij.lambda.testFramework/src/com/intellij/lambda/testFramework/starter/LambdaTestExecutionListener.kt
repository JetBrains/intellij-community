package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.junit5.TestCleanupListener
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestIdentifier
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.net.URI
import java.nio.file.Path

class LambdaTestExecutionListener : TestCleanupListener() {
  companion object {
    init {
      di = DI {
        extend(di)

        bindSingleton(tag = "teamcity.uri", overrides = true) { URI("https://buildserver.labs.intellij.net").normalize() }
        bindSingleton<CIServer>(overrides = true) {
          object : TeamCityCIServer() {
            override fun publishArtifact(source: Path, artifactPath: String, artifactName: String) {
              val testNameWithCleanedArgs: String = CurrentTestMethod.get()?.run {
                val args = arguments.filterNot { it is IdeWithLambda }
                             .takeIf { it.isNotEmpty() }
                             ?.joinToString(prefix = "(", postfix = ")", separator = " ") ?: ""

                "$clazz.$name(${args})"
              } ?: "unknown-test-name"

              super.publishArtifact(source, "$artifactPath/$testNameWithCleanedArgs", artifactName)
            }
          }
        }
      }

      ConfigureCoroutineCancellationTimeout.configure()
    }
  }

  // Due to this override IDE that is by default is launched on perClassSupervisorScope is not cancelled in Lambda tests
  override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
    if (!testIdentifier.isTest) return

    IdeInstance.publishArtifacts()
    cancelPerTestSupervisorScope(testIdentifier)
  }
}