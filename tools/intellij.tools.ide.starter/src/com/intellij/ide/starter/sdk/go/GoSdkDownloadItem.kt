package com.intellij.ide.starter.sdk.go

import com.intellij.tools.ide.performanceTesting.commands.SdkObject
import java.nio.file.Path

data class GoSdkDownloadItem(
  @JvmField val version: String,
  @JvmField val os: String,
  @JvmField val arch: String,
  private val download: () -> GoSdkPaths
) {
  private val installGoSdk = lazy { download() }

  val home: Path
    get() = installGoSdk.value.homePath

  val installPath: Path
    get() = installGoSdk.value.installPath

  fun toSdk(sdkName: String = "Go $version"): SdkObject {
    return SdkObject(
      sdkName = sdkName,
      sdkType = "Go SDK",
      sdkPath = home,
    )
  }

  override fun equals(other: Any?) = other is GoSdkDownloadItem && other.version == version && other.os == os && other.arch == arch
  override fun hashCode() = arrayOf(version, os, arch).contentHashCode()
  override fun toString(): String = "GoSdkDownloadItem(go$version-$os-$arch)"
}
