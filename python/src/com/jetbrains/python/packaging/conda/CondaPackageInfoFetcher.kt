// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.isArm64
import com.intellij.platform.eel.isX86
import com.intellij.platform.eel.isX86_64
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.HttpRequests
import com.jetbrains.python.Result
import com.jetbrains.python.mapError
import org.apache.commons.compress.compressors.CompressorStreamFactory
import java.io.IOException
import java.nio.file.FileSystems

typealias VersionSet = Set<String>
typealias PackageVersionMap = Map<String, VersionSet>

internal object CondaPackageInfoFetcher {
  private const val CONDA_BASE_URL = "https://conda.anaconda.org"
  private const val REPO_DATA_JSON = "repodata.json.bz2"
  private val logger = thisLogger()

  @RequiresBackgroundThread
  fun fetchCondaChannelPackages(channel: CondaChannel, arch: CondaArch): Result<PackageVersionMap, FetcherError> =
    try {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        return Result.Success(emptyMap())
      }
      
      logger.info("fetching packages from $channel/$arch...")
      
      HttpRequests
        .request("${CONDA_BASE_URL}/$channel/$arch/$REPO_DATA_JSON")
        .gzip(false)
        .productNameAsUserAgent()
        .tuner { connection ->
          connection.setRequestProperty("Accept-Encoding", "identity")
        }
        .connect { request ->
          request.inputStream.use { rawInputStream ->
            CompressorStreamFactory()
              .createCompressorInputStream(CompressorStreamFactory.BZIP2, rawInputStream)
              .use { decompressedInputStream ->
                CondaRepoDataStreamParser.parseVersions(decompressedInputStream)
                  .mapError { ParseError(it.message) }
              }
          }
        }
        .also { 
          logger.info("successfully fetched packages from $channel/$arch")
        }
    }
    catch (e: IOException) {
      logger.warn("failed to fetch packages from $channel/$arch", e)
      Result.Failure(FetchError(e.localizedMessage ?: "IOException"))
    }


  sealed interface FetcherError {
    val message: @NlsSafe String
  }

  data class FetchError(override val message: @NlsSafe String) : FetcherError
  data class ParseError(override val message: @NlsSafe String) : FetcherError
}

internal enum class CondaChannel(val channelName: String) {
  ANACONDA("anaconda"),
  CONDA_FORGE("conda-forge");

  override fun toString(): String = channelName
}

internal enum class CondaArch(val archString: String) {
  OSX_ARM64("osx-arm64"),
  OSX_64("osx-64"),
  WIN_ARM64("win-arm64"),
  WIN_64("win-64"),
  WIN_32("win-32"),
  LINUX_AARCH64("linux-aarch64"),
  LINUX_64("linux-64"),
  LINUX_32("linux-32"),
  FREEBSD_64("freebsd-64"),
  NOARCH("noarch");

  override fun toString(): String = archString

  companion object {
    suspend fun getCurrentArch(): CondaArch =
      when (val platform = FileSystems.getDefault().rootDirectories.first().getEelDescriptor().toEelApi().platform) {
        is EelPlatform.Darwin if platform.isArm64 -> OSX_ARM64
        is EelPlatform.Darwin if platform.isX86_64 -> OSX_64
        is EelPlatform.Windows if platform.isArm64 -> WIN_ARM64
        is EelPlatform.Windows if platform.isX86_64 -> WIN_64
        is EelPlatform.Windows if platform.isX86 -> WIN_32
        is EelPlatform.Linux if platform.isArm64 -> LINUX_AARCH64
        is EelPlatform.Linux if platform.isX86_64 -> LINUX_64
        is EelPlatform.Linux if platform.isX86 -> LINUX_32
        is EelPlatform.FreeBSD if platform.isX86_64 -> FREEBSD_64
        else -> NOARCH
      }
  }
}
