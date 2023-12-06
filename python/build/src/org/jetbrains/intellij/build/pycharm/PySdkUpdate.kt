// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Version
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.Urls
import com.intellij.util.io.HttpRequests
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.*
import org.jetbrains.jps.util.JpsChecksumUtil
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.fileSize

const val BASE_URL = "https://www.python.org/ftp/python/"
val SDKS_JSON_PATH = Path(".", "community", "python", "python-sdk", "resources", "sdks.json")


/**
 * Resources we are currently tracking.
 */
enum class TrackedResource(val filename: (String) -> String, val os: OS?, val cpuArch: CpuArch?, val source: Boolean = false) {
  SRC_TAR_XZ({ "Python-${it}.tar.xz" }, null, null, true),
  SRC_TGZ({ "Python-${it}.tgz" }, null, null, true),
  WINDOWS_X86_64({ "python-${it}-amd64.exe" }, OS.Windows, CpuArch.X86_64),
  WINDOWS_X86({ "python-${it}.exe" }, OS.Windows, CpuArch.X86),
  WINDOWS_ARM64({ "python-${it}-arm64.exe" }, OS.Windows, CpuArch.ARM64),
  MACOS({ "python-${it}-macos11.pkg" }, OS.macOS, null);

  companion object {
    fun resolve(version: String, filename: String): TrackedResource? {
      return TrackedResource.entries.firstOrNull { it.filename(version) == filename }
    }
  }
}

/**
 * All resources from sdks.json (there is no need to load and count hashes / sizes again)
 */
val RESOURCE_CACHE = SdksKeeper.sdks.python.flatMap { release ->
  buildList {
    release.sources?.let { addAll(it) }
    release.binaries?.let { bin -> addAll(bin.flatMap { it.resources }) }
  }
}.associateBy { r -> r.url }

/**
 * Retrieve all versions listed in the https://www.python.org/ftp/python/ grouped by Language Level
 */
fun getRemoteVersions(): Map<LanguageLevel, List<String>> {
  val doc: Document = Jsoup.connect(BASE_URL).get()
  val versions = doc.body().select("a").map { it.attr("href").removeSuffix("/") }
  return versions
    .filter { it.isNotBlank() and it[0].isDigit() }
    .groupBy { Version.parseVersion(it)!!.toLanguageLevel()!! }
}

/**
 * Retrieve filtered tracked resources from https://www.python.org/ftp/python/{version}
 */
fun getVersionResources(version: String): List<TrackedResource> {
  val doc: Document = Jsoup.connect("$BASE_URL/$version").get()
  val files = doc.body().select("a").map { it.attr("href") }
  return files.mapNotNull { TrackedResource.resolve(version, it) }
}

/**
 * Tries to get from the cache first. If it is a new resources then it downloads the resource file and the GPG signature of it.
 * Generates SHA256 and fileSize fields right after the GPG signature check.
 */
fun buildResource(version: String, trackedResource: TrackedResource): Resource {
  val resUrl = Urls.parseEncoded("$BASE_URL/$version/${trackedResource.filename(version)}")!!
  RESOURCE_CACHE[resUrl]?.let { return it }
  println("New resource was found ${resUrl}")

  val ascUrl = Urls.parseEncoded("${resUrl}.asc")!!
  val tempPath = PathManager.getTempPath()
  val files = listOf(resUrl, ascUrl).associateWith { url ->
    val fileName = PathUtilRt.getFileName(url.getPath())
    Paths.get(tempPath, "${System.nanoTime()}-${fileName}")
  }
  try {
    for ((url, path) in files) {
      HttpRequests.request(url).saveToFile(path.toFile(), null)
    }
    val (resFilePath, ascFilePath) = files.values.map { it.toAbsolutePath().toString() }.toTypedArray()
    runCommand("gpg", "--verify", ascFilePath, resFilePath)
    val file = files[resUrl]!!
    val sha256 = JpsChecksumUtil.getSha256Checksum(file)
    return Resource(resUrl, file.fileSize(), sha256)
  }
  finally {
    files.values.forEach { runCatching { FileUtil.delete(it) } }
  }
}

fun buildBinary(version: String, trackedResource: TrackedResource): Binary {
  return Binary(trackedResource.os!!, trackedResource.cpuArch, listOf(buildResource(version, trackedResource)))
}

fun buildRelease(version: String, resources: List<TrackedResource>, product: Product = Product.CPython): Release {
  val sources = resources.filter { it.source }.map { buildResource(version, it) }.takeIf { it.isNotEmpty() }
  val binaries = resources.filter { !it.source }.map { buildBinary(version, it) }.takeIf { it.isNotEmpty() }
  return Release(
    version = Version.parseVersion(version)!!,
    product = product,
    sources = sources,
    binaries = binaries,
  )
}

/**
 * 'OpenPGP Public Keys' section from https://www.python.org/downloads/
 */
val GPG_KEYS = arrayOf(
  "A821E680E5FA6305", // Thomas Wouters (3.12.x and 3.13.x source files and tags)
  "64E628F8D684696D", // Pablo Galindo Salgado (3.10.x and 3.11.x source files and tags)
  "FC624643487034E5", // Steve Dower (Windows binaries)
  "B26995E310250568", // Łukasz Langa (3.8.x and 3.9.x source files and tags)
  "2D347EA6AA65421D", "FB9921286F5E1540", // Ned Deily (macOS binaries, 3.7.x / 3.6.x source files and tags)
  "3A5CA953F73C700D", // Larry Hastings (3.5.x source files and tags)
  "04C367C218ADD4FF", // Benjamin Peterson (2.7.z source files and tags)
)

fun runCommand(vararg args: String) {
  val process = ProcessBuilder(*args)
    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
    .redirectError(ProcessBuilder.Redirect.INHERIT)
    .start()
  val isFinished = process.waitFor(10, TimeUnit.SECONDS)
  if (!isFinished) throw RuntimeException("Timeout on execute")
  if (process.exitValue() != 0) throw RuntimeException("Non-Zero exit code")
}

/**
 * - Updates gpg keys on startup
 * - For each Language Level and Tracked Resource gets the latest Version available
 * - Groups all Tracked Resources into bundles by version
 * -
 */
fun main() {
  runCommand("gpg", "--recv-keys", *GPG_KEYS)
  val remoteVersions = getRemoteVersions()
  val releases = mutableListOf<Release>()

  fun getLangLevelReleases(languageLevel: LanguageLevel): Map<String, List<TrackedResource>> {
    val remoteVersionsSortedDesc = remoteVersions[languageLevel]!!
      .sortedWith(Comparator.comparing<String?, Version?> { Version.parseVersion(it) }.reversed())

    val resolvedResources = mutableMapOf<TrackedResource, String>()
    for (version in remoteVersionsSortedDesc) {
      val resources = getVersionResources(version)
      resources.filter { it !in resolvedResources }.associateWithTo(resolvedResources) { version }
    }
    return resolvedResources.entries.groupBy({ (_, v) -> v }, { (k, _) -> k })
  }

  for (languageLevel in LanguageLevel.SUPPORTED_LEVELS.reversed()) {
    println("Update local versions for ${languageLevel.toPythonVersion()}")
    val langLevelReleases = getLangLevelReleases(languageLevel)
    langLevelReleases.forEach { (version, resources) ->
      releases.add(buildRelease(version, resources.sorted()))
    }
  }

  // get PyPy from the current list, we have no update logic for it
  releases.addAll(SdksKeeper.sdks.python.filter { it.product == Product.PyPy })

  val sdks = Sdks(python = releases)
  val sdksJson = SdksKeeper.serialize(sdks)
  SDKS_JSON_PATH.toFile().writeText(sdksJson)
}

