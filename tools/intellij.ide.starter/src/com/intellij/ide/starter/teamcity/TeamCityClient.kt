package com.intellij.ide.starter.teamcity

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.ide.starter.utils.logError
import com.intellij.ide.starter.utils.logOutput
import org.apache.http.client.methods.HttpGet
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

// TODO: move on to use TeamCityRest client library or stick with Okhttp
object TeamCityClient {
  val BASE_URL = "https://buildserver.labs.intellij.net"

  fun getGuestAuthUrl() = URI("$BASE_URL/guestAuth/app/rest/").normalize()

  fun get(fullUrl: URI): JsonNode {
    val request = HttpGet(fullUrl).apply {
      addHeader("Content-Type", "application/json")
      addHeader("Accept", "application/json")
    }

    logOutput("Request to TeamCity: $fullUrl")

    return HttpClient.sendRequest(request) {
      if (it.statusLine.statusCode != 200) {
        logError(InputStreamReader(it.entity.content).readText())
        throw RuntimeException("TeamCity returned not successful status code ${it.statusLine.statusCode}")
      }

      jacksonObjectMapper().readTree(it.entity.content)
    }
  }

  /** @return <BuildId, BuildNumber> */
  fun getLastSuccessfulBuild(ideInfo: IdeInfo): Pair<String, String> {
    val tag = if (!ideInfo.tag.isNullOrBlank()) "tag:${ideInfo.tag}," else ""
    val number = if (!ideInfo.buildNumber.isNullOrBlank()) "number:${ideInfo.buildNumber}," else ""
    val fullUrl = getGuestAuthUrl().resolve("builds?locator=buildType:${ideInfo.buildType},${tag}${number}status:SUCCESS,count:1")

    val build = get(fullUrl).fields().asSequence().first { it.key == "build" }.value
    val buildId = build.findValue("id").asText()
    val buildNumber = if (ideInfo.buildNumber.isNullOrBlank()) build.findValue("number").asText() else ideInfo.buildNumber
    return Pair(buildId, buildNumber)
  }

  fun downloadArtifact(buildNumber: String, artifactName: String, outFile: File) {
    val artifactUrl = getGuestAuthUrl().resolve("builds/id:$buildNumber/artifacts/content/$artifactName")
    HttpClient.download(artifactUrl.toString(), outFile)
  }

  /**
   * [source] - source path of artifact
   * [artifactPath] - new path (relative, where artifact will be present)
   * [artifactName] - name of artifact
   * [tempArtifactDirectory] - temporary directory, where artifact will be moved for preparation for publishing
   */
  fun publishTeamCityArtifacts(
    source: Path,
    artifactPath: String,
    artifactName: String = source.fileName.toString(),
    tempArtifactDirectory: Path = di.direct.instance<GlobalPaths>().testsDirectory / "teamcity-artifacts-for-publish",
    zipContent: Boolean = true,
  ) {
    if (!source.exists()) {
      logOutput("TeamCity artifact $source does not exist")
      return
    }

    fun printTcArtifactsPublishMessage(spec: String) {
      logOutput(" ##teamcity[publishArtifacts '$spec'] ")
    }

    var suffix: String
    var nextSuffix = 0
    var artifactDir: Path
    do {
      suffix = if (nextSuffix == 0) "" else "-$nextSuffix"
      artifactDir = (tempArtifactDirectory / artifactPath / (artifactName + suffix)).normalize().toAbsolutePath()
      nextSuffix++
    }
    while (artifactDir.exists())

    artifactDir.toFile().deleteRecursively()
    artifactDir.createDirectories()

    if (source.isDirectory()) {
      Files.walk(source).use { files ->
        for (path in files) {
          path.copyTo(target = artifactDir.resolve(source.relativize(path)), overwrite = true)
        }
      }
      if (zipContent) {
        printTcArtifactsPublishMessage("${artifactDir.toRealPath()}/** => $artifactPath/$artifactName$suffix.zip")
      }
      else {
        printTcArtifactsPublishMessage("${artifactDir.toRealPath()}/** => $artifactPath$suffix")
      }
    }
    else {
      val tempFile = artifactDir
      source.copyTo(tempFile, overwrite = true)
      if (zipContent) {
        printTcArtifactsPublishMessage("${tempFile.toRealPath()} => $artifactPath/${artifactName + suffix}.zip")
      }
      else {
        printTcArtifactsPublishMessage("${tempFile.toRealPath()} => $artifactPath")
      }
    }
  }
}

