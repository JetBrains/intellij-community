// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.build.server

import com.intellij.platform.devIdeConfig.DevIdeConfig
import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
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
 * [pluginDirectory] returns one plugin directory of the pool. The IDE loads it in place through `-Dplugin.path`.
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
   * Returns the plugin [directory] in the pool, the path from which the IDE loads the plugin in place.
   *
   * Each manifest line of the directory gives the path: the source of `<directory>/<below>` is `<path>/<below>`. The
   * function fails when two lines give two paths. It also fails when the path holds an entry that the manifest does not
   * name. A path in `bazel-out` can keep a file that an older build declared, and the IDE would load it.
   */
  fun pluginDirectory(directory: String): Path {
    val sources = files.get(directory)
                  ?: error("The context plugins $manifest have no plugin directory '$directory'. They have $directories")
    var root: Path? = null
    for ((destination, source) in sources) {
      val below = destination.removeSuffix("/").removePrefix("$directory/")
      var candidate = source
      repeat(source.fileSystem.getPath(below).nameCount) {
        candidate = candidate.parent
                    ?: error("The context plugins $manifest name '$destination' with the source $source, which is not below '$below'")
      }
      check(candidate.resolve(below).normalize() == source.normalize()) {
        "The context plugins $manifest name '$destination' with the source $source, which does not end with '$below'"
      }
      check(root == null || root == candidate) {
        "The context plugins $manifest place the plugin directory '$directory' in two directories, $root and $candidate"
      }
      root = candidate
    }
    val pluginDirectory = checkNotNull(root)
    check(pluginDirectory.isDirectory()) {
      "The plugin directory '$directory' of the context plugins $manifest is not a directory: $pluginDirectory"
    }
    checkNoUndeclaredEntries(directory, pluginDirectory, sources)
    return pluginDirectory
  }

  /**
   * Fails on an entry of [pluginDirectory] that [sources] do not name. A file and a link are leaves. A real directory
   * must be a parent of a named entry. The walk does not enter a directory tree, because its content is not listed.
   */
  private fun checkNoUndeclaredEntries(directory: String, pluginDirectory: Path, sources: List<Pair<String, Path>>) {
    val files = HashSet<Path>()
    val trees = HashSet<Path>()
    val parents = HashSet<Path>()
    for ((destination, source) in sources) {
      val relative = pluginDirectory.relativize(source)
      (if (destination.endsWith('/')) trees else files).add(relative)
      var parent = relative.parent
      while (parent != null) {
        parents.add(parent)
        parent = parent.parent
      }
    }
    fun undeclared(entry: Path): Nothing = error(
      "The plugin directory '$directory' at $pluginDirectory holds $entry, which the context plugins $manifest do not name. " +
      "An older build left it: delete it and build the pool again"
    )
    Files.walkFileTree(pluginDirectory, object : SimpleFileVisitor<Path>() {
      override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
        if (dir == pluginDirectory) {
          return FileVisitResult.CONTINUE
        }
        val relative = pluginDirectory.relativize(dir)
        return when (relative) {
          in trees -> FileVisitResult.SKIP_SUBTREE
          in parents -> FileVisitResult.CONTINUE
          else -> undeclared(dir)
        }
      }

      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        val relative = pluginDirectory.relativize(file)
        if (relative !in files && relative !in trees) {
          undeclared(file)
        }
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
          throw IllegalStateException(
            "The context plugins $manifest name '$relative', which is neither beside the manifest nor a runfile",
            e,
          )
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
        check(directory in files) {
          "The module index $index names the plugin directory '$directory' of '$module', which $manifest does not have"
        }
        check(modules.put(module, directory) == null) { "The module index $index names '$module' twice" }
      }
      return ContextPluginDirs(manifest, files.toSortedMap(), modules)
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
