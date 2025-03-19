// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.Release
import com.jetbrains.python.sdk.Sdks
import com.jetbrains.python.sdk.SdksKeeper
import org.jetbrains.intellij.build.pycharm.sdks.CondaUpdater
import org.jetbrains.intellij.build.pycharm.sdks.PythonUpdater
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

val SUPPORTED_LEVELS = LanguageLevel.SUPPORTED_LEVELS.filter { it.isAtLeast(LanguageLevel.PYTHON38) }
val SDKS_JSON_PATH = Path(".", "community", "python", "python-sdk", "resources", "sdks.json")

fun Sdks.releases(): List<Release> = this.python + this.conda

/**
 * All resources from sdks.json (there is no need to load and count hashes / sizes again)
 */
val RESOURCE_CACHE = SdksKeeper.sdks.releases().flatMap { release ->
  buildList {
    release.sources?.let { addAll(it) }
    release.binaries?.let { bin -> addAll(bin.flatMap { it.resources }) }
  }
}.associateBy { r -> r.url }

fun runCommand(vararg args: String) {
  val process = ProcessBuilder(*args)
    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
    .redirectError(ProcessBuilder.Redirect.INHERIT)
    .start()
  val isFinished = process.waitFor(10, TimeUnit.SECONDS)
  if (!isFinished) throw RuntimeException("Timeout on execute")
  if (process.exitValue() != 0) throw RuntimeException("Non-Zero exit code")
}

fun main() {
  val sdks = Sdks(
    python = PythonUpdater().getReleases(),
    conda = CondaUpdater().getReleases(),
  )
  val sdksJson = SdksKeeper.serialize(sdks)
  SDKS_JSON_PATH.toFile().writeText(sdksJson)
}

