// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm.sdks

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.python.pixi.common.PixiReleaseArchitecture
import com.intellij.python.pixi.common.PixiReleaseOperatingSystem
import com.intellij.python.pixi.common.getPixiReleaseAssetName
import com.intellij.util.Urls
import com.intellij.util.io.HttpRequests
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.jetbrains.python.sdk.Binary
import com.jetbrains.python.sdk.Product
import com.jetbrains.python.sdk.Release
import com.jetbrains.python.sdk.Resource
import com.jetbrains.python.sdk.ResourceType
import tools.jackson.module.kotlin.jacksonMapperBuilder

private const val PIXI_GITHUB_REPOSITORY = "prefix-dev/pixi"
private const val GITHUB_API_URL = "https://api.github.com"
private const val CACHE_REDIRECTOR_GITHUB_URL = "https://cache-redirector.jetbrains.com/github.com"

@JvmInline
private value class Sha256 private constructor(val value: String) {
  companion object {
    private val PATTERN = Regex("[0-9a-fA-F]{64}")

    fun parse(value: String): Sha256? {
      val normalized = value.removePrefix("sha256:")
      return normalized.takeIf(PATTERN::matches)?.lowercase()?.let(::Sha256)
    }
  }
}

class PixiUpdater internal constructor(private val loadReleaseJson: () -> String) {
  constructor() : this({
    HttpRequests.request("$GITHUB_API_URL/repos/$PIXI_GITHUB_REPOSITORY/releases/latest").readString()
  })

  fun getReleases(): List<Release> {
    println("Pixi Releases")
    return listOf(buildRelease(loadReleaseJson()))
  }

  internal fun buildRelease(json: String): Release {
    val githubRelease = jacksonMapperBuilder().build().readValue(json, PixiGitHubRelease::class.java)
    val version = githubRelease.tagName.removePrefix("v")
    val assets = githubRelease.assets.associateBy { it.name }
    val binaries = PIXI_TARGETS.map { target ->
      val assetName = getPixiReleaseAssetName(target.os, target.architecture)
      val asset = assets[assetName] ?: error("The Pixi release $version has no $assetName asset")
      val sha256 = Sha256.parse(asset.digest) ?: error("The Pixi release $version has no SHA-256 digest for $assetName")
      Binary(
        os = target.systemOs,
        cpuArch = target.cpuArch,
        resources = listOf(
          Resource(
            url = Urls.parseEncoded(
              "$CACHE_REDIRECTOR_GITHUB_URL/$PIXI_GITHUB_REPOSITORY/releases/download/${githubRelease.tagName}/$assetName"
            )!!,
            size = asset.size,
            sha256 = sha256.value,
            fileName = assetName,
            type = ResourceType.STANDALONE_EXECUTABLE,
          )
        ),
      )
    }
    return Release(
      version = version,
      product = Product.Pixi,
      sources = null,
      binaries = binaries,
    )
  }
}

private data class PixiTarget(
  val os: PixiReleaseOperatingSystem,
  val architecture: PixiReleaseArchitecture,
  val systemOs: OS,
  val cpuArch: CpuArch,
)

private val PIXI_TARGETS = PixiReleaseOperatingSystem.entries.flatMap { os ->
  PixiReleaseArchitecture.entries.map { architecture ->
    PixiTarget(
      os = os,
      architecture = architecture,
      systemOs = when (os) {
        PixiReleaseOperatingSystem.MACOS -> OS.macOS
        PixiReleaseOperatingSystem.LINUX -> OS.Linux
        PixiReleaseOperatingSystem.WINDOWS -> OS.Windows
      },
      cpuArch = when (architecture) {
        PixiReleaseArchitecture.ARM_64 -> CpuArch.ARM64
        PixiReleaseArchitecture.X86_64 -> CpuArch.X86_64
      },
    )
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class PixiGitHubRelease(
  @JsonProperty("tag_name") val tagName: String,
  val assets: List<PixiGitHubReleaseAsset>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class PixiGitHubReleaseAsset(
  val name: String,
  val size: Long,
  val digest: String,
)
