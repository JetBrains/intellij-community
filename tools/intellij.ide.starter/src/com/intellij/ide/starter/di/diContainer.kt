package com.intellij.ide.starter.di

import com.intellij.ide.starter.build.tool.GradleBuildTool
import com.intellij.ide.starter.build.tool.MavenBuildTool
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.community.PublicIdeResolver
import com.intellij.ide.starter.ide.*
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.path.InstallerGlobalPaths
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.runner.CodeBuilderHost
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
  bindSingleton<CodeInjector> { CodeBuilderHost() }
  bindFactory { testContext: IDETestContext -> PluginConfigurator(testContext) }
  bindSingleton<IDEResolver> { PublicIdeResolver }
  bindFactory<IdeInfo, IdeInstallator> { ideInfo ->
    if (ideInfo.productCode == "AI") {
      AndroidInstaller()
    }
    else {
      SimpleInstaller()
    }
  }
  bindFactory<IDETestContext, MavenBuildTool> { testContext: IDETestContext -> MavenBuildTool(testContext) }
  bindFactory<IDETestContext, GradleBuildTool> { testContext: IDETestContext -> GradleBuildTool(testContext) }
}
