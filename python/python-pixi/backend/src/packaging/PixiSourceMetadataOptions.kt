// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.python.pixi.backend.workspace.PixiTomlTable
import java.nio.file.Path

internal fun pixiSourceMetadataIndexArguments(
  manifest: PixiTomlTable,
  environment: String,
  feature: String?,
  installArguments: List<String>,
  workspaceRoot: Path? = null,
): List<String> {
  val pixi = manifest.getTable("tool.pixi") ?: manifest
  var index: String? = null
  val extraIndexes = linkedSetOf<String>()
  val findLinks = linkedSetOf<String>()
  val insecureHosts = linkedSetOf<String>()
  fun collect(table: PixiTomlTable?) {
    val options = table?.getTable("pypi-options") ?: return
    options.getString("index-url")?.let { index = it }
    extraIndexes.addAll(options.getArray("extra-index-urls").orEmpty().filterIsInstance<String>())
    for (link in options.getArray("find-links").orEmpty().filterIsInstance<PixiTomlTable>()) {
      val location = link.getString("url") ?: link.getString("path")?.let { path ->
        workspaceRoot?.resolve(path)?.normalize()?.toUri()?.toASCIIString()
      }
      location?.let { findLinks.add(it) }
    }
    insecureHosts.addAll(options.getArray("allow-insecure-host").orEmpty().filterIsInstance<String>())
  }
  collect(pixi)
  val definition = pixi.getTable("environments")?.get(listOf(environment))
  val features = when (definition) {
    is List<*> -> definition.filterIsInstance<String>()
    is PixiTomlTable -> definition.getArray("features").orEmpty().filterIsInstance<String>()
    else -> emptyList()
  } + listOfNotNull(feature?.takeUnless { it == "default" })
  for (name in features.distinct()) collect(pixi.getTable("feature")?.getTable(listOf(name)))
  val iterator = installArguments.iterator()
  while (iterator.hasNext()) {
    val argument = iterator.next()
    when {
      argument == "--index" && iterator.hasNext() -> index = iterator.next()
      argument.startsWith("--index=") -> index = argument.substringAfter('=')
    }
  }
  return buildList {
    index?.let { addAll(listOf("--index-url", it)) }
    for (extraIndex in extraIndexes) addAll(listOf("--extra-index-url", extraIndex))
    for (link in findLinks) addAll(listOf("--find-links", link))
    for (host in insecureHosts) addAll(listOf("--trusted-host", host))
  }
}
