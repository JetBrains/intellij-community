package com.intellij.ide.starter.di

import com.intellij.ide.starter.buildTool.BuildTool
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.ci.teamcity.NoOpCodeOwnerResolver
import com.intellij.ide.starter.ci.teamcity.CodeOwnerResolver
import com.intellij.ide.starter.community.PublicIdeDownloader
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.ScrambleToolProvider
import com.intellij.ide.starter.config.splitMode
import com.intellij.ide.starter.config.starterConfigurationStorageDefaults
import com.intellij.ide.starter.frameworks.Framework
import com.intellij.ide.starter.ide.DefaultIdeDistributionFactory
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeDistributionFactory
import com.intellij.ide.starter.ide.IdeDownloader
import com.intellij.ide.starter.ide.JBRDownloader
import com.intellij.ide.starter.ide.StarterJBRDownloader
import com.intellij.ide.starter.ide.installer.IdeInstallerFactory
import com.intellij.ide.starter.models.IdeProduct
import com.intellij.ide.starter.models.IdeProductImp
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.path.StarterGlobalPaths
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.report.AllurePath
import com.intellij.ide.starter.report.ErrorReporter
import com.intellij.ide.starter.report.ErrorReporterToCI
import com.intellij.ide.starter.report.FailureDetailsOnCI
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.report.publisher.impl.ConsoleTestResultPublisher
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.DevBuildServerRunner
import com.intellij.ide.starter.runner.IDEProcess
import com.intellij.ide.starter.runner.LocalIDEProcess
import com.intellij.ide.starter.runner.NoOpDevBuildServerRunner
import com.intellij.ide.starter.runner.RemDevTestContainer
import com.intellij.ide.starter.runner.TestContainer
import com.intellij.ide.starter.runner.TestContainerImpl
import com.intellij.ide.starter.runner.targets.LocalOnlyTargetResolver
import com.intellij.ide.starter.runner.targets.TargetResolver
import com.intellij.ide.starter.telemetry.NoopTestTelemetryService
import com.intellij.ide.starter.telemetry.TestTelemetryService
import com.intellij.tools.ide.util.common.logOutput
import org.kodein.di.DI
import org.kodein.di.bindArgSet
import org.kodein.di.bindFactory
import org.kodein.di.bindProvider
import org.kodein.di.bindSingleton
import java.net.URI
import java.nio.file.Path

/**
 * Reinitialize / override bindings for this DI container in your module before executing tests
 * https://docs.kodein.org/kodein-di/7.9/core/bindings.html
 *
 * E.g:
 * ```
 * di = DI {
 *      extend(di)
 *      bindSingleton<GlobalPaths>(overrides = true) { YourImplementationOfPaths() }
 *    }
 * ```
 * */
private var _di = DI {
  bindSingleton<GlobalPaths> { StarterGlobalPaths() }
  bindSingleton<CIServer> { NoCIServer }
  bindSingleton<ErrorReporter> { ErrorReporterToCI }
  bindSingleton<FailureDetailsOnCI> { object : FailureDetailsOnCI {} }
  bindFactory<IDETestContext, PluginConfigurator> { testContext: IDETestContext -> PluginConfigurator(testContext) }
  bindSingleton<IdeDownloader> { PublicIdeDownloader() }
  bindSingleton<IdeInstallerFactory> { IdeInstallerFactory() }
  bindSingleton<IdeDistributionFactory> { DefaultIdeDistributionFactory }
  bindSingleton<IDEProcess> { LocalIDEProcess() }

  // you can extend DI with frameworks, specific to the IDE language stack
  bindArgSet<IDETestContext, Framework>()
  importAll(ideaFrameworksDI)

  // you can extend DI with build tools, specific to the IDE language stack
  bindArgSet<IDETestContext, BuildTool>()
  importAll(ideaBuildToolsDI)

  bindSingleton<List<ReportPublisher>> { listOf(ConsoleTestResultPublisher) }

  bindSingleton<IdeProduct> { IdeProductImp }
  bindSingleton<CurrentTestMethod> { CurrentTestMethod }
  bindSingleton<ConfigurationStorage> { ConfigurationStorage(this, starterConfigurationStorageDefaults) }
  bindSingleton<TestTelemetryService> { NoopTestTelemetryService() }
  bindSingleton(tag = "teamcity.uri") { URI("https://buildserver.labs.intellij.net").normalize() }
  bindSingleton<AllurePath> {
    object : AllurePath {
      override fun reportDir(): Path {
        return GlobalPaths.instance.testsDirectory.resolve("allure")
      }
    }
  }

  bindProvider<TestContainer> { if (ConfigurationStorage.splitMode()) RemDevTestContainer() else TestContainerImpl() }
  bindSingleton<JBRDownloader> { StarterJBRDownloader }
  bindSingleton<TargetResolver> { LocalOnlyTargetResolver }
  bindSingleton<ScrambleToolProvider> { object : ScrambleToolProvider {} }
  bindSingleton<DevBuildServerRunner> { NoOpDevBuildServerRunner }
  bindSingleton<CodeOwnerResolver> { NoOpCodeOwnerResolver }
}.apply {
  logOutput("Starter DI was initialized")
}

private val lock = Any()

var di: DI = _di
  set(value) {
    synchronized(lock) {
      field = value
      logOutput(
        """Starter DI was updated by: 
        |${Thread.currentThread().stackTrace.copyOfRange(2, 5).joinToString(separator = "\n") { "- $it" }}"""
          .trimMargin()
      )
    }
  }
