package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.frontendTestCase
import com.intellij.ide.starter.ide.setFrontendEventLogsMetadataCustomPath
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.NoProject
import com.intellij.tools.ide.util.common.logOutput
import java.nio.file.Path

class RemDevTestContainer internal constructor() : TestContainer {
  override fun newContext(
    testName: String,
    testCase: TestCase<*>,
    preserveSystemDir: Boolean,
    projectHome: Path?,
    ideDataPathsProvider: IDEDataPathsProvider,
  ): IDETestContext {
    logOutput("Creating backend context")
    val backendContext = super.newContext(testName, testCase, preserveSystemDir, projectHome, ideDataPathsProvider)

    logOutput("Creating frontend context")
    val frontendTestCase = backendContext.frontendTestCase
    val frontendContext = super.newContext(
      testName,
      frontendTestCase,
      preserveSystemDir,
      if (frontendTestCase.projectInfo is NoProject) null else backendContext.resolvedProjectHome,
      ideDataPathsProvider.asFrontendDataPathsProvider(),
    )

    return IDERemDevTestContext.from(backendContext, frontendContext).apply {
      setFrontendEventLogsMetadataCustomPath(backendContext.paths.eventLogMetadataDir)
    }
  }
}
