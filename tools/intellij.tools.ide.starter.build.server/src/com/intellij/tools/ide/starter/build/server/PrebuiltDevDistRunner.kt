// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.build.server

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.runner.DevBuildServerRunner
import com.intellij.platform.devIdeConfig.DevIdeConfig
import com.intellij.tools.ide.util.common.logOutput
import org.jetbrains.intellij.build.dev.readCustomCommand
import org.jetbrains.intellij.build.dev.resolveAdditionalJvmArguments
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * Runs tests against a dev distribution that was assembled before the test process started, instead of assembling one
 * inside it.
 *
 * The distribution is named by [DevIdeConfig.CONFIG_PATH_PROPERTY] - under Bazel, an `$(rlocationpath ...)` of an
 * `intellij_dev_dist` target's config file - so a test target declares the IDE it runs against the same way it declares
 * any other input, and an unchanged tree means no assembly at all. Everything downstream is unchanged:
 * `IdeFromCodeInstaller` reads `core-classpath.txt`, resolves the JBR, and synthesizes the command line exactly as it
 * does for an assembled run.
 *
 * The distribution is fixed, which is the whole point and also the one way this can go wrong: a test asking for plugin
 * modules the assembly did not build in would otherwise run against an IDE quietly missing them. So the request is
 * checked against what the distribution says it is, and a shortfall fails the run rather than the assertions.
 */
internal class PrebuiltDevDistRunner(private val configFile: Path) : DevBuildServerRunner {
  private val config by lazy { DevIdeConfig.read(configFile) }

  override fun isDevBuildSupported(): Boolean = true

  /** A Bazel output, borrowed for the run - see [DevBuildServerRunner.ownsInstallationDirectory]. */
  override val ownsInstallationDirectory: Boolean = false

  override suspend fun readVmOptions(installationDirectory: Path): List<String> =
    org.jetbrains.intellij.build.dev.readVmOptions(installationDirectory)

  override fun readCustomCommandJvmArguments(installationDirectory: Path, command: String): List<String>? =
    readCustomCommand(installationDirectory, command)?.resolveAdditionalJvmArguments(installationDirectory)

  /** Returns the prepared IDE installation directory. */
  override suspend fun startDevBuild(ideInfo: IdeInfo): Path {
    val home = config.homePath
    check(home.isDirectory()) {
      "The dev distribution declared by '${DevIdeConfig.CONFIG_PATH_PROPERTY}' is missing: $configFile names $home, which is not a directory"
    }
    checkDistributionSatisfies(ideInfo)
    logOutput("Using the prepared dev distribution $home for $ideInfo")
    return home
  }

  private fun checkDistributionSatisfies(ideInfo: IdeInfo) {
    check(ideInfo.platformPrefix == config.platformPrefix) {
      "$ideInfo needs the '${ideInfo.platformPrefix}' product, but the dev distribution declared by " +
      "'${DevIdeConfig.CONFIG_PATH_PROPERTY}' was assembled as '${config.platformPrefix}' ($configFile)"
    }

    val requested = ideInfo.additionalModules.toSet()
    val declared = config.additionalModules.toSet()
    check(declared.containsAll(requested)) {
      "The dev distribution declared by '${DevIdeConfig.CONFIG_PATH_PROPERTY}' does not contain " +
      "${(requested - declared).sorted()}, which $ideInfo asks for.\n" +
      "  requested: ${requested.sorted()}\n" +
      "  in the distribution: ${declared.sorted()}\n" +
      "  config file: $configFile\n" +
      "Add the missing modules to the `additional_modules` of the `intellij_dev_dist` target this test declares, or " +
      "point the test at a distribution that has them."
    }

    val extra = declared - requested
    if (extra.isNotEmpty()) {
      // Harmless, and expected: one distribution serves several test targets, so it is assembled for the widest of them.
      logOutput("The dev distribution also contains ${extra.sorted()}, which $ideInfo did not ask for")
    }
  }
}
