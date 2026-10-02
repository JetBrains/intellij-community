// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.python.pyproject.PY_PROJECT_TOML
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlTable
import java.io.IOException

private val LOG = fileLogger()

/** The names of the Ruff config files, in the order that Ruff reads them in one directory. */
internal val RUFF_CONFIG_FILE_NAMES: List<String> = listOf(".ruff.toml", "ruff.toml", PY_PROJECT_TOML)

/**
 * A Ruff config file, with the Ruff settings in it.
 *
 * [settings] is the full text of a `ruff.toml` or a `.ruff.toml`, and the `tool.ruff` table of a `pyproject.toml`.
 * So two values are equal when Ruff reads the same settings from the same file.
 */
internal data class RuffConfigFile(val file: VirtualFile, val settings: String)

/**
 * The Ruff config file in [directory] itself, or `null` when it has none.
 *
 * A `pyproject.toml` counts only when it has a `tool.ruff` table, as in Ruff. A `pyproject.toml` that does not
 * parse does not count either. The Ruff server also skips such a file, but the Ruff command line stops with an error.
 */
internal fun ruffConfigIn(directory: VirtualFile): RuffConfigFile? {
  for (name in RUFF_CONFIG_FILE_NAMES) {
    val file = directory.findChild(name)?.takeUnless { it.isDirectory } ?: continue
    val text = loadText(file) ?: continue
    val settings = if (name == PY_PROJECT_TOML) ruffTableOf(text) ?: continue else text
    return RuffConfigFile(file, settings)
  }
  return null
}

/** The Ruff config file that Ruff finds for a file in [directory]: the one in [directory] or in its nearest ancestor. */
internal fun findRuffConfig(directory: VirtualFile): RuffConfigFile? =
  generateSequence(directory) { it.parent }.firstNotNullOfOrNull(::ruffConfigIn)

/**
 * The Ruff config for a file in [directory] when Ruff finds no config for it. It is the config that Ruff finds for
 * [projectDir].
 *
 * The result is `null` when [directory] or one of its ancestors has a Ruff config, because Ruff then uses that one.
 * It is also `null` when [projectDir] has no Ruff config.
 *
 * Ruff finds the config of a file in the directory of the file or in an ancestor of it. When `ruff` runs in a
 * directory with a config, a file without a config of its own gets the config of that directory. The IDE uses the
 * project directory as that directory. So a content root outside the project directory gets the project config.
 */
internal fun ruffFallbackConfig(directory: VirtualFile, projectDir: VirtualFile?): RuffConfigFile? {
  if (projectDir == null || findRuffConfig(directory) != null) return null
  return findRuffConfig(projectDir)
}

/** [ruffFallbackConfig] for the project directory of [project]. */
internal fun ruffFallbackConfig(directory: VirtualFile, project: Project): RuffConfigFile? =
  ruffFallbackConfig(directory, project.guessProjectDir())

/**
 * Whether [event] can add, remove or change a Ruff config file.
 *
 * A delete, a move or a rename of a directory fires one event for the directory alone, so each one counts. This
 * runs in the write action of each VFS change, so it reads only the name of the file.
 */
internal fun isRuffConfigEvent(event: VFileEvent): Boolean = when (event) {
  is VFilePropertyChangeEvent -> event.propertyName == VirtualFile.PROP_NAME && (event.file.isDirectory || isRuffConfigRename(event))
  is VFileDeleteEvent, is VFileMoveEvent -> event.file?.isDirectory == true || event.file?.name in RUFF_CONFIG_FILE_NAMES
  is VFileCreateEvent -> event.childName in RUFF_CONFIG_FILE_NAMES
  else -> event.file?.name in RUFF_CONFIG_FILE_NAMES
}

private fun isRuffConfigRename(event: VFilePropertyChangeEvent): Boolean =
  event.oldValue in RUFF_CONFIG_FILE_NAMES || event.newValue in RUFF_CONFIG_FILE_NAMES

/** The `tool.ruff` table of the `pyproject.toml` text [text] as JSON, or `null` when the text has none or does not parse. */
private fun ruffTableOf(text: String): String? {
  val toml = Toml.parse(text)
  if (toml.hasErrors()) return null
  return (toml.get(listOf("tool", "ruff")) as? TomlTable)?.toJson()
}

private fun loadText(file: VirtualFile): String? =
  try {
    VfsUtilCore.loadText(file)
  }
  catch (e: IOException) {
    LOG.debug("Cannot read $file", e)
    null
  }

/**
 * The workspace folders of one Ruff server that get the project config, and that config. See [ruffFallbackConfig].
 *
 * All [folders] share one [config], because each folder gets the config of the same project directory.
 */
internal data class RuffFolderFallback(val folders: List<VirtualFile>, val config: RuffConfigFile)

/** The [RuffFolderFallback] of the workspace folders [folders], or `null` when no folder gets the project config. */
internal fun ruffFolderFallback(folders: List<VirtualFile>, projectDir: VirtualFile?): RuffFolderFallback? {
  val config = projectDir?.let(::findRuffConfig) ?: return null
  val withoutConfig = folders.filter { it.isValid && findRuffConfig(it) == null }
  return if (withoutConfig.isEmpty()) null else RuffFolderFallback(withoutConfig, config)
}

/**
 * The Ruff server `initializationOptions` that give each folder of [fallback] the config file at [configPath].
 * [uriOf] gives the URI of a workspace folder, as the server gets it in `workspaceFolders`.
 *
 * - Ruff finds the options of a folder by its exact URI.
 * - Ruff reads the `settings` list only with `globalSettings` next to it.
 * - `filesystemFirst` keeps the settings of a config that Ruff finds below a folder in charge there. The server still
 *   merges the project config into it. It fills in the unset settings, and it adds to the lists such as
 *   `extend-select`. The Ruff command line uses such a config alone. The default `editorFirst` puts the project
 *   config above it.
 */
internal fun ruffInitializationOptions(
  fallback: RuffFolderFallback,
  configPath: String,
  uriOf: (VirtualFile) -> String,
): Map<String, Any> = mapOf(
  "globalSettings" to emptyMap<String, Any>(),
  "settings" to fallback.folders.map { folder ->
    mapOf(
      "workspace" to uriOf(folder),
      "configuration" to configPath,
      "configurationPreference" to "filesystemFirst",
    )
  },
)
