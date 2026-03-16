// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.io.ExternalResourcesChecker.reportUnavailability
import org.apache.maven.wrapper.DefaultDownloader
import org.apache.maven.wrapper.Downloader
import org.apache.maven.wrapper.Installer
import org.apache.maven.wrapper.PathAssembler
import org.apache.maven.wrapper.WrapperConfiguration
import org.jetbrains.idea.maven.project.BundledMaven3
import org.jetbrains.idea.maven.project.MavenInSpecificPath
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.zip.ZipFile

class MavenWrapperTestFixture(private val myProject: Project, private val myMavenVersion: String) {
  private val SNAPSHOT_REGEX: Pattern = Pattern.compile("(?<versionWithoutSnapshot>.*)-(?<timestamp>\\d{8}\\.\\d{6})-(?<build>\\d+)")


  val mavenHome: File by lazy {
    val overriddenFilePath = System.getProperty("maven.test.distribution.file")
    if (overriddenFilePath != null) {
      val file = File(overriddenFilePath)
      if (file.isDirectory) return@lazy file
      if (file.isFile) {
        return@lazy unpackDistribution(file)
      }
      throw IllegalStateException("No wrapper $overriddenFilePath")
    }
    return@lazy dowloadWrapper()
  }

  @Suppress("UsagesOfObsoleteApi")
  fun unpackDistribution(file: File): File {
    val tmpDir = FileUtil.createTempDirectory("maven", "wrapper")
    ZipFile(file).use { zip ->
      for (entry in zip.entries()) {
        if (!entry.isDirectory) {
          val outFile = File(tmpDir, entry.name)
          FileUtil.createParentDirs(outFile)
          zip.getInputStream(entry).use { input ->
            outFile.outputStream().use { output ->
              FileUtil.copy(input, output)
            }
          }
        }
      }
    }
    return tmpDir.listFiles()?.firstOrNull { it.isDirectory && it.name.startsWith("apache-maven") }?: tmpDir
  }

  private fun dowloadWrapper(): File {
    val mavenDownloader: Downloader = DefaultDownloader("Intellij IDEA", "Integration compatibility tests")
    val assembler = PathAssembler(
      File(System.getProperty("user.home"), ".m2"))
    val installer = Installer(mavenDownloader, assembler)
    val configuration = WrapperConfiguration()
    configuration.isAlwaysDownload = false
    configuration.distribution = createURI()
    configuration.distributionBase = PathAssembler.MAVEN_USER_HOME_STRING
    configuration.zipBase = PathAssembler.MAVEN_USER_HOME_STRING

    try {
      return installer.createDist(configuration)
    }
    catch (e: IOException) {
      reportUnavailability<Any?>("Maven Wrapper", e)
      throw IllegalStateException() // should never happen
    }
  }

  private fun createURI(): URI {
    if (myMavenVersion.contains("4.0.0.")) {
      return URI.create(MAVEN_4_URL_PATTERN.replace("\$version$", myMavenVersion))
    }
    if (myMavenVersion.contains("SNAPSHOT")) {
      return createSnapshotUri()
    }
    val matcher = SNAPSHOT_REGEX.matcher(myMavenVersion)

    if (matcher.matches()) {
      return createTimestampedSnapshotUri(matcher)
    }
    return URI.create(DISTRIBUTION_URL_PATTERN.replace("\$version$", myMavenVersion))
  }

  private fun createTimestampedSnapshotUri(matcher: Matcher): URI {
    require(matcher.matches()) { "matcher is not applicable" }
    val versionWithoutSnapshot = matcher.group("versionWithoutSnapshot")
    val timestamp = matcher.group("timestamp")
    val build = matcher.group("build")
    val versionWithSnapshot = versionWithoutSnapshot + "-SNAPSHOT"

    return URI.create(SNAPSHOT_URL_PATTERN
                        .replace($$"$version$", versionWithSnapshot)
                        .replace($$"$versionWithoutSnapshot$", versionWithoutSnapshot)
                        .replace($$"$timestamp$", timestamp)
                        .replace($$"$build$", build))
  }

  @Throws(Exception::class)
  private fun createSnapshotUri(): URI {
    val metadataUri = URI.create(SNAPSHOT_METADATA_URL_PATTERN.replace("\$version$", myMavenVersion))
    val timestampAndBuild = JDOMUtil.load(metadataUri.toURL()).children
      .filter { "versioning" == it.name }
      .flatMap { it.children }
      .filter { "snapshot" == it.name }
      .flatMap { it.children }
      .toList()
    var timestamp: String? = null
    var build: String? = null
    for (e in timestampAndBuild) {
      if ("timestamp" == e.getName()) {
        timestamp = e.getValue()
      }
      if ("buildNumber" == e.getName()) {
        build = e.getValue()
      }
    }

    if (build == null || timestamp == null) {
      throw Exception("cannot find last version for $myMavenVersion")
    }
    val versionWithoutSnapshot = myMavenVersion.replace("-SNAPSHOT", "")
    return URI.create(SNAPSHOT_URL_PATTERN
                        .replace($$"$version$", myMavenVersion)
                        .replace($$"$versionWithoutSnapshot$", versionWithoutSnapshot)
                        .replace($$"$timestamp$", timestamp)
                        .replace($$"$build$", build)
    )
  }

  @Throws(Exception::class)
  fun setUp() {
    MavenWorkspaceSettingsComponent.getInstance(myProject).settings.generalSettings.mavenHomeType = MavenInSpecificPath(this.mavenHome.absolutePath)
  }

  @Throws(Exception::class)
  fun tearDown() {
    MavenWorkspaceSettingsComponent.getInstance(myProject).settings.generalSettings.setMavenHomeNoFire(BundledMaven3)
  }

  companion object {
    private const val DISTRIBUTION_URL_PATTERN = $$"https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version$/apache-maven-$version$-bin.zip"

    private const val SNAPSHOT_METADATA_URL_PATTERN = $$"https://repository.apache.org/content/repositories/snapshots/org/apache/maven/apache-maven/$version$/maven-metadata.xml"

    private const val SNAPSHOT_URL_PATTERN = $$"https://repository.apache.org/content/repositories/snapshots/org/apache/maven/apache-maven/$version$/apache-maven-$versionWithoutSnapshot$-$timestamp$-$build$-bin.zip"

    private const val MAVEN_4_URL_PATTERN = $$"https://dlcdn.apache.org/maven/maven-4/$version$/binaries/apache-maven-$version$-bin.zip"
  }
}
