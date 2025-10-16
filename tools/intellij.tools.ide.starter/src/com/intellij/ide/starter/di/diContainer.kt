package com.intellij.ide.starter.di

import com.intellij.ide.starter.buildTool.BuildTool
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.community.PublicIdeDownloader
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.splitMode
import com.intellij.ide.starter.config.starterConfigurationStorageDefaults
import com.intellij.ide.starter.frameworks.Framework
import com.intellij.ide.starter.ide.*
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
import com.intellij.ide.starter.runner.*
import com.intellij.ide.starter.runner.targets.LocalOnlyTargetResolver
import com.intellij.ide.starter.runner.targets.TargetResolver
import com.intellij.ide.starter.telemetry.NoopTestTelemetryService
import com.intellij.ide.starter.telemetry.TestTelemetryService
import com.intellij.tools.ide.util.common.logOutput
import org.kodein.di.*
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
var di = DI {
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

  bindProvider<TestContainer<*>> { if (ConfigurationStorage.splitMode()) TestContainer.newInstance<RemDevTestContainer>() else TestContainer.newInstance<TestContainerImpl>() }
  bindSingleton<JBRDownloader> { StarterJBRDownloader }
  bindSingleton<TargetResolver> { LocalOnlyTargetResolver }
}.apply {
  logOutput("Starter DI was initialized")
}
