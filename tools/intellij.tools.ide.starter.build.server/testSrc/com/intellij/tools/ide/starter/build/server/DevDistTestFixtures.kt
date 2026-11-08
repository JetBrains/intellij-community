// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.build.server

import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/** Returns a distribution of the index, by default with the label `//build:<name>_dist`. */
internal fun candidate(
  name: String,
  product: String = "idea",
  additionalModules: List<String> = emptyList(),
  bundledModules: List<String> = emptyList(),
  platformBundledModules: Map<String, List<String>> = emptyMap(),
  runtimeModuleRepository: Boolean = false,
  modularLoader: Boolean = false,
  ultimate: Boolean = false,
  label: String = "//build:${name}_dist",
): DevDistCandidate = DevDistCandidate(
  name = name,
  label = label,
  product = product,
  additionalModules = additionalModules,
  bundledModules = bundledModules,
  platformBundledModules = platformBundledModules,
  runtimeModuleRepository = runtimeModuleRepository,
  modularLoader = modularLoader,
  ultimate = ultimate,
)

/**
 * Writes a context plugin pool below [root] as `context_plugin_dir` does, and returns its manifest.
 *
 * The pool has the manifest, the module index, and a link per file to a read-only packed file. Windows gets a copy
 * in place of a link, because a link there needs a privilege. The plugin directory `devkit` has the main module
 * `intellij.devkit`, and `lambda-test-plugin` has `intellij.lambda.test.plugin`.
 */
internal fun writeContextPluginPool(root: Path): Path {
  val packed = root.resolve("packed").createDirectories()
  val poolDir = root.resolve("runfiles/_main/tests").createDirectories()
  val destinations = listOf(
    "devkit/lib/devkit.jar",
    "devkit/lib/modules/intellij.devkit.core.jar",
    "lambda-test-plugin/lib/lambda-test-plugin.jar",
  )
  for (destination in destinations) {
    val source = packed.resolve(destination.replace('/', '_'))
    source.writeText(destination)
    val file = poolDir.resolve("pool").resolve(destination)
    file.parent.createDirectories()
    if (System.getProperty("os.name").startsWith("Windows")) {
      Files.copy(source, file)
    }
    else {
      Files.createSymbolicLink(file, source)
    }
    source.toFile().setReadOnly()
  }
  val manifest = poolDir.resolve("pool.context-plugins.txt")
  manifest.writeText(destinations.joinToString(separator = "") { "$it\tpool/$it\n" })
  poolDir.resolve("pool.context-plugin-modules.txt").writeText(
    "intellij.devkit\tdevkit\n" +
    "intellij.lambda.test.plugin\tlambda-test-plugin\n"
  )
  return manifest
}

internal fun writeBazelRunfilesManifest(root: Path, poolManifest: Path): Path {
  val runfiles = root.resolve("runfiles")
  val manifestOnly = root.resolve("manifest-only").createDirectories()
  val standaloneManifest = manifestOnly.resolve(poolManifest.fileName)
  Files.copy(poolManifest, standaloneManifest)

  val entries = ArrayList<String>()
  Files.walkFileTree(runfiles, setOf(FileVisitOption.FOLLOW_LINKS), Int.MAX_VALUE, object : SimpleFileVisitor<Path>() {
    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      val runfilesPath = runfiles.relativize(file).toString().replace('\\', '/')
      val absolutePath = if (file == poolManifest) standaloneManifest else file
      entries.add("$runfilesPath $absolutePath")
      return FileVisitResult.CONTINUE
    }
  })

  val manifest = root.resolve("RUNFILES_MANIFEST_FILE")
  manifest.writeText(entries.joinToString(separator = "\n", postfix = "\n"))
  return manifest
}
