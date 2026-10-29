// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.tools.ide.starter.build.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** The path of the distribution index, relative to the root of one half of the repository. */
internal const val DEV_DIST_INDEX_PATH: String = "build/dev-dist-distributions.json"

/** The Bazel repository name of the community half, as the ultimate root sees it. */
private const val COMMUNITY_REPOSITORY = "@community"

/**
 * One Bazel dev distribution of the distribution index.
 *
 * The generator of the `DevMainKt` rows writes the index. [label] is a label that `bazel build` accepts from the project
 * home. [product] is the product key of [devDistProductKey].
 *
 * [bundledModules], [platformBundledModules] and [modularLoader] come from the entry of the product in the `products`
 * section of the same index file. [platformBundledModules] maps a main module to the host platforms that bundle it, in
 * the form of [hostPlatformKey].
 *
 * [ultimate] is `true` for a distribution of the ultimate index of the monorepo. A standalone community checkout has no
 * ultimate index.
 */
internal data class DevDistCandidate(
  @JvmField val name: String,
  @JvmField val label: String,
  @JvmField val product: String,
  @JvmField val additionalModules: List<String>,
  @JvmField val bundledModules: List<String>,
  @JvmField val platformBundledModules: Map<String, List<String>>,
  @JvmField val runtimeModuleRepository: Boolean,
  @JvmField val modularLoader: Boolean,
  @JvmField val ultimate: Boolean,
) {
  /** Returns the modules that the product bundles on [hostPlatform]. */
  fun bundledModulesOn(hostPlatform: String?): Set<String> {
    val result = LinkedHashSet<String>(bundledModules)
    for ((module, platforms) in platformBundledModules) {
      if (hostPlatform in platforms) {
        result.add(module)
      }
    }
    return result
  }
}

/** The distributions of the index files, and the index files that should exist and do not. */
internal class DevDistIndex(
  @JvmField val distributions: List<DevDistCandidate>,
  @JvmField val missingFiles: List<Path>,
)

/**
 * Returns the product key of a distribution: the base IDE prefix of a frontend, then the platform prefix.
 *
 * `getProductConfiguration` of the dev build server uses the same key.
 */
internal fun devDistProductKey(platformPrefix: String, baseIdePlatformPrefixForFrontend: String?): String =
  (baseIdePlatformPrefixForFrontend ?: "") + platformPrefix

/**
 * Returns the host platform of [osName] and [osArch], in the form of `HOST_PLATFORMS` of `host_platforms.bzl`,
 * or `null` for an unknown OS or architecture.
 *
 * The values are those of the `os.name` and `os.arch` system properties. A JVM in WSL reports Linux.
 */
internal fun hostPlatformKey(osName: String, osArch: String): String? {
  val os = when {
    osName.startsWith("Windows") -> "windows"
    osName.startsWith("Mac") || osName.startsWith("Darwin") -> "darwin"
    osName.startsWith("Linux") -> "linux"
    else -> return null
  }
  val arch = when (osArch) {
    "aarch64", "arm64" -> "aarch64"
    "amd64", "x86_64" -> "x64"
    else -> return null
  }
  return "${os}_$arch"
}

/**
 * Reads the distributions of the index files under [projectHome].
 *
 * The file of [projectHome] comes first. In the monorepo, `community/` holds the index of the community half. Its labels
 * get the `@community` prefix, and the ultimate index wins for a product that both halves have. A standalone community
 * checkout has only the file of [projectHome]. A missing file gives no distributions. A file that cannot be read or
 * parsed fails with an [IllegalStateException].
 */
internal fun readDevDistIndex(projectHome: Path): DevDistIndex {
  val missingFiles = ArrayList<Path>()
  val communityRoot = projectHome.resolve("community")
  val monorepo = Files.isDirectory(communityRoot)
  val own = readDevDistIndexFile(projectHome.resolve(DEV_DIST_INDEX_PATH), labelPrefix = "", ultimate = monorepo, missingFiles)
  if (!monorepo) {
    return DevDistIndex(own, missingFiles)
  }

  val community = readDevDistIndexFile(communityRoot.resolve(DEV_DIST_INDEX_PATH), labelPrefix = COMMUNITY_REPOSITORY, ultimate = false, missingFiles)
  val ownProducts = own.mapTo(HashSet()) { it.product }
  return DevDistIndex(own + community.filter { it.product !in ownProducts }, missingFiles)
}

private fun readDevDistIndexFile(file: Path, labelPrefix: String, ultimate: Boolean, missingFiles: MutableList<Path>): List<DevDistCandidate> {
  if (!Files.isRegularFile(file)) {
    missingFiles.add(file)
    return emptyList()
  }

  val text = try {
    Files.readString(file)
  }
  catch (e: IOException) {
    throw IllegalStateException("Cannot read the dev distribution index $file: $e", e)
  }
  try {
    val index = Json.parseToJsonElement(text).jsonObject
    val products = index.field("products").jsonObject
    return index.field("distributions").jsonArray.map { element ->
      val entry = element.jsonObject
      val name = entry.string("name")
      val label = entry.string("label")
      val product = entry.string("product")
      val productEntry = products.get(product)?.jsonObject
                         ?: throw IllegalArgumentException("the distribution '$name' names the product '$product', which 'products' does not have")
      DevDistCandidate(
        name = name,
        label = if (label.startsWith("@")) label else labelPrefix + label,
        product = product,
        additionalModules = entry.strings("additionalModules"),
        bundledModules = productEntry.strings("bundledModules"),
        platformBundledModules = productEntry.field("platformBundledModules").jsonObject.mapValues { (_, platforms) ->
          platforms.jsonArray.map { it.jsonPrimitive.content }
        },
        runtimeModuleRepository = entry.flag("runtimeModuleRepository"),
        modularLoader = productEntry.flag("modularLoader"),
        ultimate = ultimate,
      )
    }
  }
  catch (e: IllegalArgumentException) {
    throw IllegalStateException("Cannot read the dev distribution index $file: ${e.message}", e)
  }
}

private fun JsonObject.field(key: String): JsonElement = get(key) ?: throw IllegalArgumentException("an object has no '$key'")

private fun JsonObject.string(key: String): String = field(key).jsonPrimitive.content

private fun JsonObject.flag(key: String): Boolean =
  field(key).jsonPrimitive.booleanOrNull ?: throw IllegalArgumentException("'$key' is not a boolean")

private fun JsonObject.strings(key: String): List<String> = field(key).jsonArray.map { it.jsonPrimitive.content }

/**
 * Selects the distribution for a request, or returns `null` when no distribution matches.
 *
 * A distribution matches when it has the [product], when [missingDevDistModules] finds none of [modules], and, if
 * [runtimeModuleRepository] is requested, when it composes a runtime module repository. A modular-loader product
 * always composes one. The bundled modules are those of [hostPlatform].
 *
 * Of the matching distributions, the one with the fewest additional modules wins. Then a distribution without a
 * runtime module repository wins, then the first name.
 */
internal fun selectDevDist(
  candidates: List<DevDistCandidate>,
  product: String,
  modules: Collection<String>,
  runtimeModuleRepository: Boolean,
  hostPlatform: String?,
): DevDistCandidate? {
  return candidates
    .asSequence()
    .filter { it.product == product }
    .filter { !runtimeModuleRepository || it.runtimeModuleRepository || it.modularLoader }
    .filter { missingDevDistModules(modules, it.additionalModules, it.bundledModulesOn(hostPlatform)).isEmpty() }
    .minWithOrNull(compareBy<DevDistCandidate> { it.additionalModules.size }.thenBy { it.runtimeModuleRepository }.thenBy { it.name })
}
