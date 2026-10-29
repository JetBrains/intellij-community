// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.tools.ide.starter.build.server

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.ScrambleToolProvider
import com.intellij.ide.starter.config.includeRuntimeModuleRepositoryInIde
import com.intellij.ide.starter.config.useDockerContainer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.runner.DevBuildServerRunner
import com.intellij.platform.buildScripts.concurrency.withLockInterruptibly
import com.intellij.platform.buildScripts.devLaunch.readCustomCommand
import com.intellij.platform.buildScripts.devLaunch.resolveAdditionalJvmArguments
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kodein.di.direct
import org.kodein.di.instance
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * Products whose Bazel dev distribution has known gaps (MRI-5558).
 *
 * The Rider distribution lacks the files of `RiderProperties.registerDistFiles`. The Gateway distribution has no
 * embedded frontend.
 */
private val LEGACY_ENGINE_PRODUCTS = setOf("Rider", "Gateway")

/** Serializes the Bazel builds of all [LocalBazelDevDistRunner] instances in this JVM. */
private val devDistBuildLock = ReentrantLock()

/**
 * Builds the Bazel dev distribution of a test with `bazel build`, and runs the test on it with [PrebuiltDevDistRunner].
 *
 * A local test run that declares no distribution gets this runner. The runner selects the distribution from the
 * distribution index of [projectHome] with [selectDevDist]. Each [startDevBuild] call decides again with
 * [legacyEngineReason], because a test can change the scrambling, the Docker mode or the runtime module repository after
 * the binding. [DevBuildServerRunnerImpl] builds the IDE when the legacy engine has a reason or no distribution matches.
 * The log names the reason.
 *
 * A lock of this JVM serializes the builds. The Bazel server lock serializes the builds of two test JVMs. No lock keeps
 * a build from rewriting a distribution while an IDE of another test JVM runs from it.
 */
internal class LocalBazelDevDistRunner(private val projectHome: Path) : DevBuildServerRunner {
  override fun isDevBuildSupported(): Boolean = true

  /** The runner mixes Bazel outputs and the legacy `out/dev-run` directory, so it deletes none of them. */
  override val ownsInstallationDirectory: Boolean = false

  override fun readVmOptions(installationDirectory: Path): List<String> =
    com.intellij.platform.buildScripts.devLaunch.readVmOptions(installationDirectory)

  override fun readCustomCommandJvmArguments(installationDirectory: Path, command: String): List<String>? =
    readCustomCommand(installationDirectory, command)?.resolveAdditionalJvmArguments(installationDirectory)

  override fun startDevBuild(ideInfo: IdeInfo): Path {
    val distribution = findDistribution(ideInfo) ?: return DevBuildServerRunnerImpl.startDevBuild(ideInfo)
    val configFile = devDistBuildLock.withLockInterruptibly { buildDevDist(projectHome, distribution.label) }
    return PrebuiltDevDistRunner(configFile, origin = "built from ${distribution.label}").startDevBuild(ideInfo)
  }

  private fun findDistribution(ideInfo: IdeInfo): DevDistCandidate? {
    val reason = legacyEngineReason(ideInfo) ?: run {
      val product = devDistProductKey(ideInfo.platformPrefix, ideInfo.baseIdePlatformPrefixForFrontend)
      val runtimeModuleRepository = ConfigurationStorage.includeRuntimeModuleRepositoryInIde()
      val index = readDevDistIndex(projectHome)
      val hostPlatform = hostPlatformKey(System.getProperty("os.name"), System.getProperty("os.arch"))
      val distribution = selectDevDist(index.distributions, product, ideInfo.additionalModules, runtimeModuleRepository, hostPlatform)
      if (distribution != null) {
        logOutput("Dev build server: $ideInfo runs on the Bazel dev distribution ${distribution.label}")
        return distribution
      }

      val noMatch = "no distribution of the index has the product '$product', the modules ${ideInfo.additionalModules.sorted()}" +
                    (if (runtimeModuleRepository) " and a runtime module repository" else "")
      if (index.missingFiles.isEmpty()) noMatch else index.missingFiles.joinToString { "the index file $it is missing" } + ", and $noMatch"
    }
    logOutput("Dev build server: the legacy engine builds $ideInfo, because $reason")
    return null
  }
}

/** Returns [legacyEngineReason] for [ideInfo] and the current scrambling and Docker settings of the test. */
internal fun legacyEngineReason(ideInfo: IdeInfo): String? = legacyEngineReason(
  scrambling = di.direct.instance<ScrambleToolProvider>().get() != null,
  docker = ConfigurationStorage.useDockerContainer(),
  platformPrefix = ideInfo.platformPrefix,
  baseIdePlatformPrefixForFrontend = ideInfo.baseIdePlatformPrefixForFrontend,
)

/**
 * Returns why the legacy engine builds the IDE of one [LocalBazelDevDistRunner.startDevBuild] call, or `null` when a
 * Bazel dev distribution can serve it.
 *
 * The checks go in this order: scrambling, a Docker run, and then a product of [LEGACY_ENGINE_PRODUCTS]. A Bazel dev
 * distribution has no scrambling. A Docker run needs a Linux IDE, and the host OS can be another one.
 */
internal fun legacyEngineReason(scrambling: Boolean, docker: Boolean, platformPrefix: String, baseIdePlatformPrefixForFrontend: String?): String? {
  return when {
    scrambling -> "the test asks for scrambling, and a Bazel dev distribution has none"
    docker -> "the test runs the IDE in a Docker container, so the target OS is not the host OS"
    platformPrefix in LEGACY_ENGINE_PRODUCTS || baseIdePlatformPrefixForFrontend in LEGACY_ENGINE_PRODUCTS ->
      "the Bazel dev distribution of '${devDistProductKey(platformPrefix, baseIdePlatformPrefixForFrontend)}' has known gaps"
    else -> null
  }
}

/**
 * Builds the Bazel dev distribution [label] in [projectHome], and returns its `ide.config` file.
 *
 * The Bazel output goes to the test log. The build event file names the `ide.config` file.
 */
internal fun buildDevDist(projectHome: Path, label: String): Path {
  // Not in the temporary directory of the JVM: under `bazel run`, it is in the execution root, and the build deletes it.
  val directory = Files.createDirectories(projectHome.resolve("out/dev-dist-builds"))
  val eventFile = Files.createTempFile(directory, label.replace(Regex("[^A-Za-z0-9._-]"), "_") + "-", ".build-events.json")
  try {
    val command = listOf(
      projectHome.resolve("bazel.cmd").pathString,
      "build",
      label,
      "--build_event_json_file=$eventFile",
      // Every URI then names the local file.
      "--nobuild_event_json_file_path_conversion",
    )
    logOutput("Dev build server: running ${command.joinToString(" ")} in $projectHome")
    val start = System.nanoTime()
    val exitCode = runStreamingOutput(projectHome, command)
    check(exitCode == 0) {
      "Bazel cannot build the dev distribution $label: `bazel build` exited with code $exitCode. The Bazel output is in the test log above. " +
      "On Windows, an IDE that runs from the home of the same distribution blocks the rebuild. Stop that IDE, then run the test again."
    }
    logOutput("Dev build server: built $label in ${TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start)} s")

    val configFileName = ideConfigFileName(label)
    return readBuiltFiles(eventFile).firstOrNull { it.name == configFileName }
           ?: error("The build event file of `bazel build $label` names no $configFileName ($eventFile)")
  }
  finally {
    Files.deleteIfExists(eventFile)
  }
}

/** Returns the file name of the `ide.config` output of the distribution [label]: the target name and `.ide.config`. */
internal fun ideConfigFileName(label: String): String = label.substringAfterLast(':').substringAfterLast('/') + ".ide.config"

/**
 * Runs [command] in [workingDirectory], writes its output to the test log, and returns the exit code.
 *
 * An interrupt kills the Bazel client and its descendants. The Bazel server then stops the build.
 */
private fun runStreamingOutput(workingDirectory: Path, command: List<String>): Int {
  val process = ProcessBuilder(command).directory(workingDirectory.toFile()).redirectErrorStream(true).start()
  process.outputStream.close()
  val reader = thread(isDaemon = true, name = "bazel build output") {
    process.inputStream.bufferedReader().useLines { lines ->
      for (line in lines) {
        logOutput("  [bazel] $line")
      }
    }
  }
  try {
    val exitCode = process.waitFor()
    reader.join()
    return exitCode
  }
  finally {
    if (process.isAlive) {
      logOutput("Dev build server: stopping the Bazel client (pid=${process.pid()})")
      process.descendants().forEach { it.destroyForcibly() }
      process.destroyForcibly()
      process.waitFor(1, TimeUnit.MINUTES)
    }
  }
}

/**
 * Returns the local files that the `namedSetOfFiles` events of a JSON build event file name.
 *
 * The build must run with `--nobuild_event_json_file_path_conversion`, so that each URI is a `file:` URI.
 */
internal fun readBuiltFiles(eventFile: Path): List<Path> {
  val files = ArrayList<Path>()
  Files.readAllLines(eventFile).forEachIndexed { index, line ->
    if (line.isBlank()) {
      return@forEachIndexed
    }

    val event = try {
      Json.parseToJsonElement(line).jsonObject
    }
    catch (e: IllegalArgumentException) {
      throw IllegalStateException("Cannot read line ${index + 1} of the build event file $eventFile: ${e.message}", e)
    }
    val set = event.get("namedSetOfFiles")?.jsonObject ?: return@forEachIndexed
    for (file in set.get("files")?.jsonArray ?: return@forEachIndexed) {
      val uri = file.jsonObject.get("uri")?.jsonPrimitive?.content ?: continue
      if (uri.startsWith("file:")) {
        files.add(Path.of(URI(uri)))
      }
    }
  }
  return files
}
