// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend

import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import com.intellij.platform.eel.EelDescriptor
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.sdk.backend.resolveExecutable
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.intellij.python.uv.backend.cli.uv.UvPythonEntry
import com.intellij.python.uv.backend.runtime.uvCli
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path

/**
 * One interpreter uv reported, as much of it as a caller needs to offer it as a base Python.
 *
 * Everything here is read off uv's own answer, so no interpreter is executed to build one: uv already states the
 * version, and running each of the thirty-odd interpreters it lists to learn what it just said would be the slowest
 * part of the whole listing.
 */
@ApiStatus.Internal
data class UvSystemPython(
  val pythonBinary: Path,
  val languageLevel: LanguageLevel,
  /** The version as uv reports it, pre-release suffix and all: `3.13.5`, `3.15.0b4`. */
  val version: @NlsSafe String,
  /** A free-threaded (no-GIL) build, which is worth telling apart from the ordinary one of the same version. */
  val freeThreaded: Boolean,
  /** Installed by uv itself, under `uv python dir` — as opposed to one uv merely found on the machine. */
  val uvManaged: Boolean,
)

/**
 * The Pythons uv knows about, and uv's own way to install one.
 *
 * Where uv is on the machine it sees more than the IDE's own scan does — the interpreters it manages, and the ones
 * every other installer left on the machine — and it answers in one process rather than one scan per source.
 *
 * The methods mirror `SystemPythonService` deliberately, without implementing it: that interface builds `SystemPython`,
 * whose constructor is private to its own module, and whose public entry point registers each path as user-provided —
 * neither of which suits a bulk listing. When this moves to an extension point, this is the shape that moves.
 *
 * uv sees a version manager only through its shims. A pyenv interpreter is reported as `~/.pyenv/shims/python3.12` and
 * never as the real `~/.pyenv/versions/3.12.1/bin/python3.12`, and versions pyenv holds but does not currently point at
 * are not reported at all. So this is a different set from the IDE's scan, not a superset of it.
 */
/**
 * How long a listing stands before uv is asked again. Short beside the ten minutes `SystemPythonService` caches for,
 * and long enough to cover one opening of the widget, which is what asks several times over.
 */
private const val CACHE_TTL_MS: Long = 30_000

@ApiStatus.Internal
object UvSystemPythonService {
  /** True when uv can be run for [fileSystem]'s machine, which is what makes this service usable there at all. */
  suspend fun isAvailable(fileSystem: FileSystem<PathHolder.Eel>): Boolean = executableOrNull(fileSystem) != null

  /**
   * Every interpreter uv reports as present, newest first, one entry per interpreter.
   *
   * uv names the same install under several paths — `python3` beside `python3.14`, a shim beside what it points at — so
   * the paths are resolved and repeats dropped. Without that the same interpreter is offered twice.
   *
   * [baseDir] is where uv runs, so it reads that project's `.python-version` and `pyproject.toml`, exactly as the uv
   * node's own listing does.
   */
  suspend fun findSystemPythons(fileSystem: FileSystem<PathHolder.Eel>, baseDir: Path): List<UvSystemPython> {
    val snapshot = snapshot(fileSystem, baseDir)
    return snapshot.entries.toSystemPythons(snapshot.uvPythonDir)
  }

  /**
   * Everything `uv python list` reported for [baseDir], installed and downloadable alike.
   *
   * For a caller that wants uv's own entries rather than the interpreters they amount to — the uv node builds its
   * version rows from them. Shares the one listing, so asking costs no process of its own.
   */
  suspend fun listEntries(fileSystem: FileSystem<PathHolder.Eel>, baseDir: Path): List<UvPythonEntry> =
    snapshot(fileSystem, baseDir).entries

  /** Where uv installs the interpreters it manages, which is how one it installed is told from one it merely found. */
  suspend fun uvPythonDir(fileSystem: FileSystem<PathHolder.Eel>, baseDir: Path): Path? =
    snapshot(fileSystem, baseDir).uvPythonDir

  /**
   * Installs [target] with uv and returns the interpreter that landed.
   *
   * The path is read back from a fresh listing rather than guessed from `uv python dir`: uv decides the layout of what
   * it installs, and the listing is where it says so.
   */
  suspend fun installPython(fileSystem: FileSystem<PathHolder.Eel>, baseDir: Path, target: String): PyResult<Path> {
    val runtime = runtimeOrNull(fileSystem, baseDir)
                  ?: return PyResult.localizedError(PyUvBundle.message("uv.system.python.executable.not.found"))
    runtime.uvCli().python().install(target).getOr { return it }
    // The listing this service holds was taken before the install and no longer describes the machine.
    invalidate()
    // Found again by the same request uv was given. A full identifier (`cpython-3.8.20-macos-aarch64-none`) names one
    // build and comes back as that one; a bare version (`3.8`) comes back as whichever build uv chose for it.
    val installed = listEntries(fileSystem, baseDir)
                      .firstOrNull { it.path != null && (it.key == target || it.versionParts.languageLevel == target) }
                      ?.path
                    ?: return PyResult.localizedError(PyUvBundle.message("uv.system.python.not.installed", target))
    return PyResult.success(Path.of(installed))
  }

  /** Drops what was listed, so the next question asks uv again. */
  fun invalidate() {
    snapshots.clear()
  }

  /**
   * What uv last said about [baseDir]'s machine, asking it again only when nothing recent is held.
   *
   * uv is asked once for all of it. Listing costs a third of a second — it walks the machine — while the widget builds
   * its base-Python list once per tool node, so four nodes opening together asked four times over, and each of those
   * asked twice more for the directory and for the downloadable versions. One answer, shared.
   *
   * The whole thing runs under one lock rather than a lock per project: it is the nodes opening at the same moment that
   * this exists to collapse, and letting them all through to find an empty cache would defeat it. The wait is one uv
   * call, which is what the first of them was going to pay anyway.
   *
   * [CACHE_TTL_MS] is short next to the ten minutes `SystemPythonService` holds the same kind of answer for, and an
   * install through this service clears it outright, so a version that arrives through the widget is never missed.
   */
  private suspend fun snapshot(fileSystem: FileSystem<PathHolder.Eel>, baseDir: Path): Snapshot = lock.withLock {
    val key = SnapshotKey(fileSystem.eelDescriptor, baseDir)
    snapshots[key]?.takeIf { System.currentTimeMillis() - it.takenAt < CACHE_TTL_MS }?.let { return it }
    val runtime = runtimeOrNull(fileSystem, baseDir) ?: return Snapshot(emptyList(), null, System.currentTimeMillis())
    val entries = runtime.uvCli().python().list().getOrNull().orEmpty()
    // Asked only to tell uv's own installs from the ones it merely found. uv's `key` cannot answer that: it carries the
    // full version (`cpython-3.15.0b4-…`) while the directory it installs into carries the short one
    // (`cpython-3.15-…`), so the two do not match for a pre-release.
    val uvPythonDir = runtime.uvCli().python().dir().getOrNull()?.trim()?.let { Path.of(it) }
    return Snapshot(entries, uvPythonDir, System.currentTimeMillis()).also { snapshots[key] = it }
  }

  private val lock = Mutex()
  private val snapshots = mutableMapOf<SnapshotKey, Snapshot>()

  /** One machine and one project directory: uv reads that directory's `.python-version`, so it is part of the answer. */
  private data class SnapshotKey(val descriptor: EelDescriptor?, val baseDir: Path)

  private class Snapshot(val entries: List<UvPythonEntry>, val uvPythonDir: Path?, val takenAt: Long)

  private suspend fun runtimeOrNull(fileSystem: FileSystem<PathHolder.Eel>, baseDir: Path): PyToolRuntime? {
    val uvExecutable = executableOrNull(fileSystem) ?: return null
    return PyToolRuntime(binary = fileSystem.getBinaryToExec(uvExecutable, baseDir), execOptions = ExecOptions())
  }

  private suspend fun executableOrNull(fileSystem: FileSystem<PathHolder.Eel>): PathHolder.Eel? =
    fileSystem.resolveExecutable(UvPyTool.getInstance())
}

/**
 * The entries that stand for an interpreter on this machine, deduplicated by where each one really is.
 *
 * uv's `symlink` is relative to the entry's own directory, so it cannot be used as a path on its own; the link is
 * followed on disk instead. A path that cannot be resolved — a broken link, a machine that is not this one — keeps the
 * path uv gave, which is still the best answer available for it.
 */
internal fun List<UvPythonEntry>.toSystemPythons(uvPythonDir: Path?): List<UvSystemPython> {
  val byRealPath = LinkedHashMap<Path, UvSystemPython>()
  for (entry in this) {
    val binary = entry.path?.let { Path.of(it) } ?: continue
    val level = entry.languageLevelOrNull() ?: continue
    byRealPath.putIfAbsent(binary.realPathOrSelf(), UvSystemPython(
      pythonBinary = binary,
      languageLevel = level,
      version = entry.version,
      freeThreaded = entry.isFreeThreaded,
      // Under `uv python dir` is what uv installed; anything else it merely found on the machine.
      uvManaged = uvPythonDir != null && binary.normalize().startsWith(uvPythonDir.normalize()),
    ))
  }
  return byRealPath.values.sortedWith(
    // The ordering the widget shows them in: the newest usable first, and a free-threaded build after the ordinary one
    // of the same version, since it is the unusual choice.
    compareBy<UvSystemPython> { it.freeThreaded }.thenByDescending { it.languageLevel }
  )
}

/** The `major.minor` of an entry as a [LanguageLevel], or null for one this IDE has no level for. */
private fun UvPythonEntry.languageLevelOrNull(): LanguageLevel? =
  LanguageLevel.fromPythonVersion(versionParts.languageLevel)

/** The path with every symlink followed, or the path itself when it cannot be resolved. */
private fun Path.realPathOrSelf(): Path =
  try {
    toRealPath()
  }
  catch (_: IOException) {
    normalize()
  }
