// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm.sdks

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Version
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.io.HttpRequests
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.*
import org.jetbrains.intellij.build.pycharm.RESOURCE_CACHE
import org.jetbrains.jps.util.JpsChecksumUtil
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.fileSize

data class CondaVersion(val presentation: String, val version: Version, val build: Int) : Comparable<CondaVersion> {
  override fun compareTo(other: CondaVersion) = compareValuesBy(this, other, { it.version }, { it.build })
  override fun toString(): String {
    return presentation
  }
}

interface CondaVersionParser {
  fun parse(version: String): CondaVersion?
}

class AnacondaVersionParser : CondaVersionParser {
  override fun parse(version: String): CondaVersion? {
    val args = VERSION_REGEX.matchEntire(version)?.destructured?.toList()?.map { d -> d.takeIf { it.isNotEmpty() }?.toInt() ?: 0 }
    return args?.let { (year, month, build) ->
      CondaVersion(
        presentation = version,
        version = Version(year, month, 0),
        build = build
      )
    }
  }

  companion object {
    val VERSION_REGEX = """(20\d{2})\.(\d{2})(-\d+)?""".toRegex()
  }
}

class MinicondaVersionParser : CondaVersionParser {
  override fun parse(version: String): CondaVersion? {
    val args = VERSION_REGEX.matchEntire(version)?.destructured?.toList()?.map { it.toInt() }
    return args?.let { (year, month, release, build) ->
      CondaVersion(
        presentation = version,
        version = Version(2000 + year, month, release),
        build = build
      )
    }
  }

  companion object {
    val VERSION_REGEX = """(\d{2})\.(\d+)\.(\d+)-(\d+)""".toRegex()
  }
}

val CONDA_VERSION_PARSERS = mapOf(
  Product.Anaconda to AnacondaVersionParser(),
  Product.Miniconda to MinicondaVersionParser()
)


class CondaUpdater {
  fun getReleases(): List<Release> {
    println("Conda Releases")
    val indexed = SdksKeeper.sdks.conda
    val nonIndexed = CONDA_PRODUCT_URLS.flatMap { (product, condaResources) ->
      condaResources.findNonIndexed().map { (condaVersion, binaries) ->
        Release(
          version = condaVersion.toString(),
          product = product,
          sources = null,
          binaries = binaries,
        )
      }
    }
    return (nonIndexed + indexed).sortedByDescending { r -> CONDA_VERSION_PARSERS[r.product]!!.parse(r.version) }
  }

  class MinicondaResources : CondaResources(Product.Miniconda, "${ANACONDA_REPO_URL}/miniconda/") {
    override fun buildBinary(url: Url, condaResource: CondaResource): Pair<CondaVersion, Binary> {
      val match = FILENAME_REGEX.matchEntire(condaResource.filename)
                  ?: error("${condaResource.filename} doesn't match: ${FILENAME_REGEX}")

      val (pyVersion, version, _, _, _, _, osValue, cpuArchValue) = match.destructured
      val languageLevel = LanguageLevel.fromPythonVersion("3.${pyVersion}}")
                          ?: error("${condaResource.filename} has wrong Python version: ${pyVersion}")
      val condaVersion = parseVersion(version)
                         ?: error("${condaResource.filename} has wrong Conda version: ${version}")

      return condaVersion to Binary(
        os = OS.fromString(osValue),
        cpuArch = CpuArch.fromString(cpuArchValue),
        resources = listOf(buildResource(url, condaResource)),
        tags = listOf(languageLevel.name)
      )
    }

    companion object {
      private val FILENAME_REGEX = """^Miniconda3-py3(\d+)_(${MinicondaVersionParser.VERSION_REGEX})-(\w+)-(\w+)\.\w+$""".toRegex()
    }
  }

  class AnacondaResources : CondaResources(Product.Anaconda, "${ANACONDA_REPO_URL}/archive/") {
    private val basePythons = mutableMapOf<CondaVersion, LanguageLevel>()
    override fun buildBinary(url: Url, condaResource: CondaResource): Pair<CondaVersion, Binary> {
      val match = FILENAME_REGEX.matchEntire(condaResource.filename)
                  ?: error("${condaResource.filename} doesn't match: ${FILENAME_REGEX}")

      val (version, _, _, _, osValue, cpuArchValue) = match.destructured
      val condaVersion = parseVersion(version)
                         ?: error("${condaResource.filename} has wrong Conda version: ${version}")
      val languageLevel = basePythons.getOrPut(condaVersion) {
        print("Specify base python version for Anaconda ${condaVersion.presentation}:")
        LanguageLevel.fromPythonVersion(readln())!!
      }
      return condaVersion to Binary(
        os = OS.fromString(osValue),
        cpuArch = CpuArch.fromString(cpuArchValue),
        resources = listOf(buildResource(url, condaResource)),
        tags = listOf(languageLevel.name)
      )
    }

    companion object {
      private val FILENAME_REGEX = """^Anaconda3-(${AnacondaVersionParser.VERSION_REGEX})-(\w+)-(\w+)\.\w+$""".toRegex()
    }
  }

  abstract class CondaResources(product: Product, private val baseUrl: String) {
    private val versionParser = CONDA_VERSION_PARSERS[product]!!

    data class CondaResource(val filename: String, val size: String, val lastModified: String, val sha256: String)

    fun parseVersion(version: String): CondaVersion? {
      return versionParser.parse(version)
    }

    private fun getRemoteResources(): Map<Url, CondaResource> {
      val doc: Document = Jsoup.connect(baseUrl).get()
      val rows = doc.body().select("tr")
      return rows.mapNotNull { tr ->
        val columns = tr.select("td").map { td -> td.text() }
        columns.takeIf { it.size == 4 }?.let { (filename, size, lastModified, sha256) ->
          Urls.parseEncoded(baseUrl + filename)?.let { it to CondaResource(filename, size, lastModified, sha256) }
        }
      }.toMap()
    }

    fun findNonIndexed(): Map<CondaVersion, List<Binary>> {
      return getRemoteResources()
        .filterKeys { it !in RESOURCE_CACHE }
        .mapNotNull { (url, res) ->
          try {
            buildBinary(url, res)
          }
          catch (e: IllegalStateException) {
            if (!listOf("-latest-", "-uninstaller-").any { it in res.filename } && res.lastModified.isNotEmpty()) {
              res.lastModified.takeIf { it.substring(0..3).toInt() > 2023 }?.let {
                // already verified everything up to 2024, for 2024+ files have to check and fix manually
                throw e
              }
            }
            null
          }
        }
        .groupBy({ it.first }, { it.second })
    }

    abstract fun buildBinary(url: Url, condaResource: CondaResource): Pair<CondaVersion, Binary>

    fun buildResource(url: Url, condaResource: CondaResource): Resource {
      val tempDirPath = PathManager.getTempPath()
      val destinationFilePath = Paths.get(tempDirPath, "${System.nanoTime()}-${condaResource.filename}")
      print("Processing ${url} to ${destinationFilePath} ... ")
      return try {
        if (Files.exists(destinationFilePath)) {
          println(" Already exists")
        }
        else {
          println(" Downloading")
          HttpRequests.request(url).saveToFile(destinationFilePath.toFile(), null)
        }
        val sha256 = JpsChecksumUtil.getSha256Checksum(destinationFilePath)
        if (sha256 != condaResource.sha256) {
          error("Wrong sha256 ($sha256 != ${condaResource.sha256}) of a resource ${url}")
        }
        Resource(
          url = url,
          size = destinationFilePath.fileSize(),
          sha256 = sha256,
          fileName = condaResource.filename,
          type = ResourceType.ofFileName(condaResource.filename)
        )
      }
      finally {
        FileUtil.delete(destinationFilePath)
      }
    }

  }

  companion object {
    private const val ANACONDA_REPO_URL = "https://repo.anaconda.com"
    val CONDA_PRODUCT_URLS = mapOf(
      Product.Miniconda to MinicondaResources(),
      Product.Anaconda to AnacondaResources(),
    )
  }
}



