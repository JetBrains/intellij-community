package com.intellij.tools.ide.starter.build.server

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.DISnapshot
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.DevBuildServerRunner
import com.intellij.openapi.application.ArchivedCompilationContextUtil
import com.intellij.openapi.application.PathManager
import com.intellij.platform.bazel.runfiles.BazelRunfiles
import com.intellij.platform.devIdeConfig.DevIdeConfig
import com.intellij.tools.ide.util.common.logOutput
import org.jetbrains.intellij.build.TestingOptions
import org.junit.platform.launcher.TestExecutionListener
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Path

fun initStarterDevBuildServerDI() {
  val runner = devBuildServerRunner()
  di = DI {
    extend(di)
    bindSingleton<DevBuildServerRunner>(overrides = true) { runner }
  }
  DISnapshot.initSnapshot(di, overwrite = true)
}

/**
 * Assembling the IDE in this process is the default; a test target that declares an already-assembled distribution gets
 * it instead.
 *
 * A test under Bazel runfiles that gives the legacy engine no Bazel inputs gets [UndeclaredDevDistRunner], see
 * [legacyEngineHasNoBazelInputs]. Its first IDE build fails with the declaration that the test target needs.
 *
 * A local run outside Bazel gets [LocalBazelDevDistRunner]. It builds a Bazel dev distribution, because the module
 * outputs of a gutter run do not contain the jars of the whole product. [legacyRunnerReason] names the runs that keep
 * the legacy engine.
 *
 * The choice is made here, in the one place that binds the runner, rather than by a second module contributing a second
 * [TestExecutionListener]: service-loader order is unspecified, so two listeners overriding the same binding could
 * silently restore the assembling runner and spend minutes building an IDE the test target already had.
 */
private fun devBuildServerRunner(): DevBuildServerRunner {
  val configFile = DevIdeConfig.declaredConfigFile()
  if (configFile != null) {
    logOutput("Dev build server: using the distribution declared by '${DevIdeConfig.CONFIG_PATH_PROPERTY}' ($configFile)")
    val distribution = DeclaredDevDist(configFile, declaredContextPlugins(CONTEXT_PLUGINS_PROPERTY))
    val frontendConfigFile = declaredFile(FRONTEND_CONFIG_PATH_PROPERTY)
    val frontendContextPlugins = declaredContextPlugins(FRONTEND_CONTEXT_PLUGINS_PROPERTY)
    if (frontendConfigFile == null) {
      check(frontendContextPlugins == null) {
        "'$FRONTEND_CONTEXT_PLUGINS_PROPERTY' names the context plugins of a frontend distribution, but '$FRONTEND_CONFIG_PATH_PROPERTY' is not set"
      }
      return PrebuiltDevDistRunner(listOf(distribution))
    }
    logOutput("Dev build server: using the frontend distribution declared by '$FRONTEND_CONFIG_PATH_PROPERTY' ($frontendConfigFile)")
    return PrebuiltDevDistRunner(
      distributions = listOf(distribution, DeclaredDevDist(frontendConfigFile, frontendContextPlugins)),
      origin = "declared by '${DevIdeConfig.CONFIG_PATH_PROPERTY}' and '$FRONTEND_CONFIG_PATH_PROPERTY'",
    )
  }
  val underBazel = BazelRunfiles.isRunningFromBazel
  if (legacyEngineHasNoBazelInputs(underBazel, System.getProperty(ArchivedCompilationContextUtil.BAZEL_TARGETS_JSON_FILE_PROPERTY))) {
    logOutput("Dev build server: the test runs under Bazel runfiles, declares no distribution and gives the legacy engine no Bazel inputs")
    return UndeclaredDevDistRunner
  }
  val reason = legacyRunnerReason(
    // A CI server can be bound after this listener, so the environment decides too.
    onCi = System.getenv("TEAMCITY_VERSION") != null || CIServer.instance.isBuildRunningOnCI,
    testJvmOfBuildScripts = System.getProperty(TestingOptions.TEST_JVM_OF_BUILD_SCRIPTS_PROPERTY).toBoolean(),
    underBazel = underBazel,
  )
  if (reason != null) {
    logOutput("Dev build server: the legacy engine builds the IDE, because $reason")
    return DevBuildServerRunnerImpl
  }
  return LocalBazelDevDistRunner(PathManager.getHomeDir())
}

/** The file that [property] names, as [DevIdeConfig.resolveConfigFile] resolves it, or `null` when [property] is unset. */
private fun declaredFile(property: String): Path? {
  return System.getProperty(property)?.takeIf { it.isNotBlank() }?.let(DevIdeConfig::resolveConfigFile)
}

/** The manifest of the context plugin pool that [property] names, or `null` when [property] is unset. See [ContextPluginDirs]. */
private fun declaredContextPlugins(property: String): Path? {
  val manifest = declaredFile(property) ?: return null
  logOutput("Dev build server: using the context plugins declared by '$property' ($manifest)")
  return manifest
}

/**
 * Returns why a run that declares no distribution binds [DevBuildServerRunnerImpl], or `null` for [LocalBazelDevDistRunner].
 *
 * The checks go in this order: a CI run, a test JVM of the build scripts, and a run under Bazel. A CI run keeps the
 * runner that owns its installation directory. A test JVM of the build scripts, for example of `tests.cmd`, has the
 * outputs of every module, and a nested `bazel build` can delete its temporary directory. A run under Bazel gets here
 * only with the Bazel inputs of the legacy engine, see [legacyEngineHasNoBazelInputs].
 */
internal fun legacyRunnerReason(onCi: Boolean, testJvmOfBuildScripts: Boolean, underBazel: Boolean): String? {
  return when {
    onCi -> "the run is on CI"
    testJvmOfBuildScripts -> "the build scripts started the test JVM ('${TestingOptions.TEST_JVM_OF_BUILD_SCRIPTS_PROPERTY}')"
    underBazel -> "the test runs under Bazel"
    else -> null
  }
}

class DevBuildServerListener : TestExecutionListener {
  companion object {
    init {
      initStarterDevBuildServerDI()
    }
  }
}
