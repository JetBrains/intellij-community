// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.standalone

import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText

/**
 * Finds the files that more than one class jar in the closure of a Standalone layer contains, with the resources.
 * A fat JAR keeps only one copy of such a file, so the result depends on the jar order.
 *
 * `StandaloneMavenArtifactsTest` checks the module sources, because a Bazel module source jar has another layout than the Maven one.
 *
 * The check reports a file only if the layer adds a jar with it, so each lower layer reports its own duplicates.
 * The report lists the [Jar.name] of each jar with the file, so a new jar with a known duplicate changes the report.
 * The check ignores `module-info` files, because a fat JAR cannot keep them anyway.
 * It also ignores [ALLOWED_DUPLICATES].
 */
fun main(args: Array<String>) {
  val (manifest, golden) = args.map { Path.of(it).toAbsolutePath() }

  // group -> jars
  val jars = LinkedHashMap<String, MutableList<Jar>>()
  for (line in manifest.readLines().filter { it.isNotBlank() }) {
    val (group, owner, path) = line.split(' ', limit = 3)
    jars.getOrPut(group) { ArrayList() }.add(Jar(owner, path))
  }

  val layerOwners = collectOwners(jars.getValue("layer"))
  val lowerOwners = jars.filterKeys { it != "layer" }.values.map { collectOwners(it) }
  val duplicates = layerOwners.filter { (file, owners) -> owners.size > 1 && lowerOwners.none { it[file] == owners } }

  val actualText = buildString {
    for ((file, owners) in duplicates.toSortedMap()) {
      append(file).append('\n')
      owners.map { it.name }.sorted().forEach { append("  ").append(it).append('\n') }
    }
  }.trim()

  val expectedText = golden.readText().trim()
  if (actualText != expectedText) {
    System.err.println("Actual ${golden.name}:\n$actualText")
    error("Duplicate files differ from the golden file ${golden.name}")
  }
}

/**
 * The files that describe one jar, so every jar can have them. A fat JAR build writes its own copy or drops them.
 */
private val ALLOWED_DUPLICATES: Set<String> = setOf(
  "META-INF/MANIFEST.MF",
  // the entry index of the IntelliJ zip reader, with the entry offsets of one jar
  "__index__",
)

/**
 * A jar in the closure of a layer.
 *
 * @param owner the target that builds the jar, or `-` for a library jar.
 * @param path the runfiles path of the jar.
 */
private data class Jar(val owner: String, val path: String) {
  /**
   * The name of the jar in the report: the target of a module jar, or the file name of a library jar without the version.
   * The repository and the library version are not in it, so a library update does not change the report.
   */
  val name: String
    get() = if (owner != "-") owner else Path.of(path).name.removeSuffix(".jar").replace(VERSION_SUFFIX, "")
}

private val VERSION_SUFFIX = Regex("""-\d.*$""")

/**
 * Maps each file to the jars that contain it.
 */
private fun collectOwners(jars: List<Jar>): Map<String, Set<Jar>> {
  val owners = HashMap<String, MutableSet<Jar>>()
  for (jar in jars) {
    ZipFile(Path.of(jar.path).toAbsolutePath().toFile()).use { zip ->
      for (entry in zip.entries()) {
        val name = entry.name
        if (entry.isDirectory || name.substringAfterLast('/').startsWith("module-info.") || name in ALLOWED_DUPLICATES) {
          continue
        }
        owners.getOrPut(name) { LinkedHashSet() }.add(jar)
      }
    }
  }
  return owners
}
