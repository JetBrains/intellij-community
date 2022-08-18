package com.intellij.ide.starter.di

import com.intellij.ide.starter.buildTool.BuildToolDefaultProvider
import com.intellij.ide.starter.buildTool.BuildToolProvider
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.community.PublicIdeDownloader
import com.intellij.ide.starter.ide.*
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeProduct
import com.intellij.ide.starter.models.IdeProductImp
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.path.InstallerGlobalPaths
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.report.FailureDetailsOnCI
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.report.publisher.impl.ConsoleTestResultPublisher
import com.intellij.ide.starter.report.publisher.impl.QodanaTestResultPublisher
import com.intellij.ide.starter.runner.CodeBuilderHost
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.DI
import org.kodein.di.bindFactory
import org.kodein.di.bindSingleton

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
  bindSingleton<GlobalPaths> { InstallerGlobalPaths() }
  bindSingleton<CIServer> { NoCIServer }
  bindSingleton<FailureDetailsOnCI> { object : FailureDetailsOnCI {} }
  bindSingleton<CodeInjector> { CodeBuilderHost() }
  bindFactory { testContext: IDETestContext -> PluginConfigurator(testContext) }
  bindSingleton<IdeDownloader> { PublicIdeDownloader }
  bindFactory<IdeInfo, IdeInstallator> { ideInfo ->
    if (ideInfo.productCode == IdeProductProvider.AI.productCode) {
      AndroidInstaller()
    }
    else {
      SimpleInstaller()
    }
  }
  bindFactory<IDETestContext, BuildToolProvider> { testContext: IDETestContext -> BuildToolDefaultProvider(testContext) }
  bindSingleton<List<ReportPublisher>> { listOf(ConsoleTestResultPublisher, QodanaTestResultPublisher) }
  bindSingleton<IdeProduct> { IdeProductImp }
  bindSingleton<CurrentTestMethod> { CurrentTestMethod }
}.apply {
  logOutput("DI was initialized")
}
