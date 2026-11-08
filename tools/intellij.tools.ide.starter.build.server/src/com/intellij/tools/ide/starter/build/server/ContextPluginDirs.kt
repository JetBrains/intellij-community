// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.build.server

import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.devIdeConfig.DevIdeConfig
import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readLines

/**
 * The context plugins of one pool: the plugin directories of a `context_plugin_dir` target.
 *
 * The [manifest] `<pool>.context-plugins.txt` has one `<destination>\t<path>` line per file or directory tree. The destination
 * is relative to `<context>/plugins` and starts with the plugin directory. The path is relative to the directory of the manifest.
 * A destination that ends with `/` names a directory tree, and its path ends with `/` too.
 * The module index `<pool>.context-plugin-modules.txt` is a sibling of the manifest. It has one
 * `<main module>\t<plugin directory>` line per plugin.
 *
 * [installPluginDirectory] copies one plugin directory into a `plugins` directory of an IDE context.
 */
@ApiStatus.Internal
class ContextPluginDirs private constructor(
  val manifest: Path,
  /** The files and the directory trees of each plugin directory: the destination relative to `<context>/plugins` to its source. */
  private val files: Map<String, List<Pair<String, Path>>>,
  /** The plugin directory of each plugin, by the main module of the plugin. */
  val modules: Map<String, String>,
) {
  /** The plugin directories of the pool, sorted. */
  val directories: Set<String>
    get() = files.keys

  /** Returns the plugin directory of [module], or `null` when no plugin of the pool has [module] as its main module. */
  fun directoryOf(module: String): String? = modules.get(module)

  /**
   * Copies the plugin [directory] to `<pluginsDir>/<directory>` and returns that path.
   *
   * The copy replaces an existing directory as a whole. Each file is a copy of the real file behind its link, so the
   * IDE context owns it. A directory tree is copied recursively in the same way. The directories of the copy are writable.
   */
  fun installPluginDirectory(directory: String, pluginsDir: Path): Path {
    val sources = files.get(directory) ?: error("The context plugins $manifest have no plugin directory '$directory'. They have $directories")
    val target = pluginsDir.resolve(directory)
    NioFiles.deleteRecursively(target)
    for ((destination, source) in sources) {
      val file = pluginsDir.resolve(destination.removeSuffix("/"))
      if (destination.endsWith('/')) {
        copyTree(source, file)
      }
      else {
        file.parent.createDirectories()
        copyFile(source, file)
      }
    }
    return target
  }

  private fun copyTree(source: Path, target: Path) {
    Files.walkFileTree(source, setOf(FileVisitOption.FOLLOW_LINKS), Int.MAX_VALUE, object : SimpleFileVisitor<Path>() {
      override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
        target.resolve(source.relativize(dir).toString()).createDirectories()
        return FileVisitResult.CONTINUE
      }

      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        copyFile(file, target.resolve(source.relativize(file).toString()))
        return FileVisitResult.CONTINUE
      }
    })
  }

  companion object {
    const val MANIFEST_SUFFIX: String = ".context-plugins.txt"
    const val MODULE_INDEX_SUFFIX: String = ".context-plugin-modules.txt"

    /**
     * Reads the pool that [value] names, an absolute path or a runfiles path, as [DevIdeConfig.resolveConfigFile] resolves it.
     *
     * A path of the manifest or of the module index can be missing beside the resolved manifest. Then the path resolves as
     * the runfiles path `<directory of value>/<path>`. A pool in another repository, such as `@community`, needs that.
     * A directory tree can also resolve from the runfiles manifest, which lists its files rather than the directory.
     */
    fun resolve(value: String): ContextPluginDirs = resolve(value, DevIdeConfig::resolveConfigFile)

    internal fun resolve(value: String, resolveRunfile: (String) -> Path): ContextPluginDirs {
      val directory = value.substringBeforeLast('/', missingDelimiterValue = "")
      return read(resolveRunfile(value)) { relative -> resolveRunfile(if (directory.isEmpty()) relative else "$directory/$relative") }
    }

    /** Reads the pool of [manifest] and its module index. Each path must be beside [manifest]. */
    fun read(manifest: Path): ContextPluginDirs = read(manifest, fallback = null)

    private fun read(manifest: Path, fallback: ((String) -> Path)?): ContextPluginDirs {
      val manifestName = manifest.name
      require(manifestName.endsWith(MANIFEST_SUFFIX)) { "The context plugin manifest $manifest does not end with '$MANIFEST_SUFFIX'" }
      val base = manifest.parent
      fun locate(relative: String): Path {
        val beside = base.resolve(relative)
        if (fallback == null || beside.exists()) {
          return beside
        }
        try {
          return fallback(relative)
        }
        catch (e: IllegalStateException) {
          throw IllegalStateException("The context plugins $manifest name '$relative', which is neither beside the manifest nor a runfile", e)
        }
      }
      val files = LinkedHashMap<String, MutableList<Pair<String, Path>>>()
      for ((destination, relative) in readTabSeparated(manifest)) {
        val directory = destination.substringBefore('/')
        check(directory.isNotEmpty() && directory != destination) {
          "The context plugin manifest $manifest names '$destination', which is not a file of a plugin directory"
        }
        val tree = destination.endsWith('/')
        check(tree == relative.endsWith('/')) {
          "The context plugin manifest $manifest maps '$destination' to '$relative': a directory tree needs '/' at the end of both"
        }
        val source = locate(relative)
        check(source.exists()) { "The context plugin manifest $manifest names '$relative', and $source does not exist" }
        check(source.isDirectory() == tree) {
          "The context plugin manifest $manifest names '$relative' as a ${if (tree) "directory tree" else "file"}, and $source is not one"
        }
        files.computeIfAbsent(directory) { ArrayList() }.add(destination to source)
      }

      val index = locate(manifestName.removeSuffix(MANIFEST_SUFFIX) + MODULE_INDEX_SUFFIX)
      check(index.exists()) { "The module index $index of the context plugins $manifest does not exist" }
      val modules = LinkedHashMap<String, String>()
      for ((module, directory) in readTabSeparated(index)) {
        check(directory in files) { "The module index $index names the plugin directory '$directory' of '$module', which $manifest does not have" }
        check(modules.put(module, directory) == null) { "The module index $index names '$module' twice" }
      }
      return ContextPluginDirs(manifest, files.toSortedMap(), modules)
    }

    /** A stream copy gives the file the default permissions: a Bazel output is read-only. */
    private fun copyFile(source: Path, target: Path) {
      Files.newInputStream(source.toRealPath()).use { Files.copy(it, target) }
    }

    private fun readTabSeparated(file: Path): List<Pair<String, String>> {
      return file.readLines().filter { it.isNotBlank() }.map { line ->
        val separator = line.indexOf('\t')
        check(separator > 0 && separator < line.length - 1) { "$file has the line '$line', which is not two tab-separated fields" }
        line.substring(0, separator) to line.substring(separator + 1)
      }
    }
  }
}
