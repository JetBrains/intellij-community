// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.build.server

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.runner.DevBuildServerRunner
import com.intellij.platform.buildScripts.devLaunch.readCustomCommand
import com.intellij.platform.buildScripts.devLaunch.resolveAdditionalJvmArguments
import com.intellij.platform.devIdeConfig.DevIdeConfig
import com.intellij.tools.ide.util.common.logOutput
import org.jetbrains.intellij.build.distributionExpirationProblem
import java.nio.file.Path
import kotlin.io.path.exists
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
 * The distribution is fixed, which is the whole point and also the two ways this can go wrong: a test asking for plugin
 * modules the assembly did not build in would otherwise run against an IDE quietly missing them, and a distribution
 * reused past its EAP expiration would come up showing a modal instead of a frame. Both are decidable here, from bytes
 * already on disk, so both fail the run with a named reason rather than the assertions - or, worse, a timeout.
 *
 * A split-mode test starts two IDEs, the backend and the JetBrains Client frontend, so it declares two distributions:
 * [distributions] holds one distribution per product, and each IDE gets the one assembled for its platform prefix.
 *
 * A distribution can have a pool of context plugins. A requested module that the distribution does not contain is
 * available when a plugin of the pool has it as its main module. [contextPluginPaths] returns the directory of that
 * plugin in the pool, and the IDE loads it in place through `-Dplugin.path`.
 *
 * [origin] says where the distributions come from, for example "declared by 'idea.ide.config.path'". Each error
 * message names it.
 */
internal class PrebuiltDevDistRunner(
  private val distributions: List<DeclaredDevDist>,
  private val origin: String = "declared by '${DevIdeConfig.CONFIG_PATH_PROPERTY}'",
) : DevBuildServerRunner {
  constructor(configFile: Path, origin: String = "declared by '${DevIdeConfig.CONFIG_PATH_PROPERTY}'") :
    this(listOf(DeclaredDevDist(configFile)), origin)

  private class LoadedDevDist(
    @JvmField val configFile: Path,
    @JvmField val config: DevIdeConfig.Content,
    @JvmField val contextPlugins: ContextPluginDirs?,
  )

  private val loaded: List<LoadedDevDist> by lazy {
    distributions.map { LoadedDevDist(it.configFile, DevIdeConfig.read(it.configFile), it.contextPlugins?.let(ContextPluginDirs::read)) }
  }

  override fun isDevBuildSupported(): Boolean = true

  /** A Bazel output, borrowed for the run - see [DevBuildServerRunner.ownsInstallationDirectory]. */
  override val ownsInstallationDirectory: Boolean = false

  override fun readVmOptions(installationDirectory: Path): List<String> =
    com.intellij.platform.buildScripts.devLaunch.readVmOptions(installationDirectory)

  override fun readCustomCommandJvmArguments(installationDirectory: Path, command: String): List<String>? =
    readCustomCommand(installationDirectory, command)?.resolveAdditionalJvmArguments(installationDirectory)

  /** Returns the prepared IDE installation directory. */
  override fun startDevBuild(ideInfo: IdeInfo): Path {
    val distribution = distributionFor(ideInfo)
    val home = distribution.config.homePath
    check(home.isDirectory()) {
      "The dev distribution $origin is missing: ${distribution.configFile} names $home, which is not a directory"
    }
    checkDistributionSatisfies(ideInfo, distribution)
    checkDistributionIsNotExpired(home, distribution.configFile)
    logOutput("Using the prepared dev distribution $home for $ideInfo")
    return home
  }

  /** The plugin directories of the context plugin pool that hold the requested modules the distribution does not contain. */
  override fun contextPluginPaths(ideInfo: IdeInfo): List<Path> {
    val distribution = distributionFor(ideInfo)
    val contextPlugins = distribution.contextPlugins ?: return emptyList()
    val config = distribution.config
    val missing = missingDevDistModules(ideInfo.additionalModules, config.additionalModules, config.bundledModules)
    return contextPluginPaths(missing, contextPlugins, config.homePath.resolve("plugins"))
  }

  /**
   * Refuses to launch an IDE that would come up expired.
   *
   * An EAP build whose stamped date is outside its expiration period - in either direction: too old, or in the future,
   * which the licensing code reads as a tampered clock - shows a modal "build expired" dialog a few seconds after
   * startup and shuts down. From the outside that is indistinguishable from a hung IDE, and costs the whole launch
   * timeout to find out. The same verdict is pure arithmetic over a value sitting in the distribution, so it is decided
   * here in milliseconds, before a single process is started.
   *
   * A dev distribution stamps no build date and therefore never trips this; the check is what makes it loud if one ever
   * starts stamping one again.
   */
  private fun checkDistributionIsNotExpired(home: Path, configFile: Path) {
    val problem = distributionExpirationProblem(home) ?: return
    error(
      "The dev distribution $origin would not start: $problem.\n" +
      "  distribution: $home\n" +
      "  config file: $configFile\n" +
      "The IDE would show the 'EAP build expired' dialog and shut down. Reassemble the distribution: a dev build must " +
      "leave the '__BUILD_DATE__' placeholder in ApplicationInfo.xml so that its build time is resolved at startup."
    )
  }

  /** The declared distribution assembled for the product of [ideInfo], keyed as in `build/dev-build.json`. */
  private fun distributionFor(ideInfo: IdeInfo): LoadedDevDist {
    val index = declaredDevDistFor(loaded.map { it.config.platformPrefix }, ideInfo.platformPrefix, ideInfo.baseIdePlatformPrefixForFrontend)
    return loaded.getOrNull(index ?: -1) ?: error(
      "$ideInfo needs the '${devDistProductKey(ideInfo.platformPrefix, ideInfo.baseIdePlatformPrefixForFrontend)}' product, " +
      "but the dev distributions $origin were assembled as " +
      loaded.joinToString { "'${it.config.platformPrefix}' (${it.configFile})" } +
      if (loaded.size == 1) ". A split-mode test declares its frontend distribution by '$FRONTEND_CONFIG_PATH_PROPERTY'" else ""
    )
  }

  private fun checkDistributionSatisfies(ideInfo: IdeInfo, distribution: LoadedDevDist) {
    val config = distribution.config
    val contextPlugins = distribution.contextPlugins
    val requested = ideInfo.additionalModules.toSet()
    val declared = config.additionalModules.toSet()
    val unavailable = unavailableDevDistModules(requested, declared, config.bundledModules, contextPlugins)
    check(unavailable.isEmpty()) {
      "The dev distribution $origin and its context plugins do not contain $unavailable, which $ideInfo asks for.\n" +
      "  requested: ${requested.sorted()}\n" +
      "  declared in the distribution: ${declared.sorted()}\n" +
      "  bundled in the distribution: ${config.bundledModules.size} modules (see '${DevIdeConfig.BUNDLED_MODULES_KEY}')\n" +
      "  context plugins: ${contextPlugins?.let { "${it.modules.keys.sorted()} (${it.manifest})" } ?: "none"}\n" +
      "  config file: ${distribution.configFile}\n" +
      "A module is available when the distribution declares or bundles it, or when a context plugin has it as its main module. " +
      "Add each missing plugin module to the row of the test module in `tests` of `build/starter-bazel-tests.json`. " +
      "Then run `./build/jpsModelToBazel.cmd` and `bazel run //platform/buildScripts:plugin-model-tool`."
    }

    val extra = declared - requested
    if (extra.isNotEmpty()) {
      // Harmless, and expected: one distribution serves several test targets, so it is assembled for the widest of them.
      logOutput("The dev distribution also contains ${extra.sorted()}, which $ideInfo did not ask for")
    }
  }
}

/**
 * Returns the index of the declared distribution that serves an IDE of [platformPrefix], or `null` when none does.
 *
 * [declaredProductKeys] holds the product of each declared distribution, the `platform.prefix` of its [DevIdeConfig],
 * which is a `build/dev-build.json` key. A frontend has the platform prefix `JetBrainsClient` and the key of its base
 * IDE, see [devDistProductKey].
 */
internal fun declaredDevDistFor(declaredProductKeys: List<String>, platformPrefix: String, baseIdePlatformPrefixForFrontend: String?): Int? {
  val index = declaredProductKeys.indexOf(devDistProductKey(platformPrefix, baseIdePlatformPrefixForFrontend))
  return if (index < 0) null else index
}

/**
 * The config file of the JetBrains Client distribution of a split-mode test, beside the backend one of
 * [DevIdeConfig.CONFIG_PATH_PROPERTY]. Only the test harness reads it: a dev launcher starts one IDE.
 */
internal const val FRONTEND_CONFIG_PATH_PROPERTY: String = "idea.ide.frontend.config.path"

/**
 * The manifest of the context plugin pool of the distribution of [DevIdeConfig.CONFIG_PATH_PROPERTY], as a runfiles path
 * or an absolute path. See [ContextPluginDirs].
 */
internal const val CONTEXT_PLUGINS_PROPERTY: String = "idea.ide.context.plugins"

/** The manifest of the context plugin pool of the distribution of [FRONTEND_CONFIG_PATH_PROPERTY]. */
internal const val FRONTEND_CONTEXT_PLUGINS_PROPERTY: String = "idea.ide.frontend.context.plugins"

/** A declared distribution: its [configFile], and the manifest of its context plugin pool, or `null` when it has no pool. */
internal class DeclaredDevDist(@JvmField val configFile: Path, @JvmField val contextPlugins: Path? = null)

/**
 * Returns the [requested] modules that neither a dev distribution nor its [contextPlugins] contain, sorted.
 *
 * See [missingDevDistModules] for the modules of the distribution. A pool contains a module when one of its plugins
 * has the module as its main module.
 */
internal fun unavailableDevDistModules(
  requested: Collection<String>,
  declared: Collection<String>,
  bundled: Collection<String>,
  contextPlugins: ContextPluginDirs?,
): List<String> {
  return missingDevDistModules(requested, declared, bundled).filter { contextPlugins?.directoryOf(it) == null }
}

/**
 * Returns the plugin directories of the plugins of [contextPlugins] that have the [missing] modules as main modules,
 * sorted by directory name. The IDE loads each of them in place, so the context gets no copy.
 *
 * [missing] holds the requested modules that the distribution does not contain, see [missingDevDistModules]. The
 * function fails on a module that no plugin of the pool has. It also fails on a plugin directory that
 * [homePluginsDir], the `plugins` directory of the distribution, already holds: the IDE would load two copies.
 *
 * A pool directory is a read-only Bazel output. An uninstall or an update of such a plugin by the plugin manager would
 * change the runfiles of the test.
 */
internal fun contextPluginPaths(missing: Collection<String>, contextPlugins: ContextPluginDirs, homePluginsDir: Path): List<Path> {
  val directories = missing.mapTo(sortedSetOf()) { module ->
    contextPlugins.directoryOf(module) ?: error("The context plugins ${contextPlugins.manifest} have no plugin with the main module '$module'")
  }
  if (directories.isEmpty()) {
    return emptyList()
  }

  for (directory in directories) {
    check(!homePluginsDir.resolve(directory).exists()) {
      "The context plugin directory '$directory' of ${contextPlugins.manifest} is also in the distribution, at ${homePluginsDir.resolve(directory)}"
    }
  }
  val paths = directories.map { contextPlugins.pluginDirectory(it) }
  logOutput("Loads the context plugins ${missing.sorted()} of ${contextPlugins.manifest} in place from $paths")
  return paths
}

/**
 * Returns the [requested] modules that a dev distribution does not contain, sorted.
 *
 * A distribution contains a module when it is [declared] or [bundled]. A plugin that the product bundles is in the
 * distribution, also when no `additional_modules` entry names it.
 */
internal fun missingDevDistModules(requested: Collection<String>, declared: Collection<String>, bundled: Collection<String>): List<String> {
  val available = HashSet<String>(declared)
  available.addAll(bundled)
  return requested.filterNot { it in available }.distinct().sorted()
}
