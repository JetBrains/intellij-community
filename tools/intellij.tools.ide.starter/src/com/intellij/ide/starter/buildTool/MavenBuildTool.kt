package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.process.findAndKillProcessesBySubstring
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.util.io.findOrCreateDirectory
import com.intellij.tools.ide.performanceTesting.commands.dto.MavenArchetypeInfo
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.io.delete
import org.jetbrains.jps.model.serialization.JpsMavenSettings.getMavenRepositoryPath
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.time.Duration.Companion.minutes

open class MavenBuildTool(testContext: IDETestContext) : BuildTool(BuildToolType.MAVEN, testContext) {
  companion object {
    /**
     * ~/.m2
     */
    val MAVEN_M2_REPO_PATH: Path
      get() = Path.of(getMavenRepositoryPath())

    private const val MAVEN_DAEMON_NAME = "MavenServerIndexerMain"
    private fun destroyMavenIndexerProcessIfExists() {
      findAndKillProcessesBySubstring(MAVEN_DAEMON_NAME)
    }
  }

  init {
    EventsBus.subscribe(GradleBuildTool::javaClass, 1.minutes) { event: IdeAfterLaunchEvent ->
      if (event.runContext.testContext === testContext) {
        destroyMavenIndexerProcessIfExists()
      }
    }
  }

  private val temporaryMavenM3CachePath: Path
    get() = testContext.paths.tempDir.resolve(".m3")

  val temporaryMavenM3UserSettingsPath: Path
    get() = temporaryMavenM3CachePath.resolve("settings.xml")

  val temporaryMavenM3RepoPath: Path
    get() = temporaryMavenM3CachePath.resolve("repository")


  fun useNewMavenLocalRepository(): MavenBuildTool {
    temporaryMavenM3RepoPath.createDirectories()
    testContext.applyVMOptionsPatch { addSystemProperty("maven.repo.local", temporaryMavenM3RepoPath.toString()) }
    return this
  }

  fun removeMavenConfigFiles(): MavenBuildTool {
    logOutput("Removing Maven config files in ${testContext.resolvedProjectHome} ...")
    for (file in testContext.resolvedProjectHome.walk()) {
      if (file.isRegularFile() && file.name == "pom.xml") {
        file.delete()
        logOutput("File ${file} is deleted")
      }
    }

    return this
  }

  fun setLogLevel(logLevel: LogLevel) {
    testContext.applyVMOptionsPatch {
      configureLoggers(logLevel, "org.jetbrains.idea.maven")
    }
  }

  fun setPropertyInPomXml(
    propertyName: String,
    propertyValue: String,
    modulePath: Path = testContext.resolvedProjectHome,
  ): MavenBuildTool {
    val pomXml = modulePath.resolve("pom.xml")
    val propertiesTag = "<properties>"
    val closePropertiesTag = "</properties>"
    val newProperty = "<$propertyName>$propertyValue</$propertyName>"
    val text = pomXml.bufferedReader().use { it.readText() }
      .run {
        if (contains(propertiesTag)) {
          if (contains(propertyName)) {
            replace("(?<=<$propertyName>)(.*)(?=</$propertyName>)".toRegex(), propertyValue)
          }
          else {
            replace(propertiesTag, "$propertiesTag\n$newProperty")
          }
        }
        else {
          val closeModelVersionTag = "</modelVersion>"
          replace(closeModelVersionTag, "$closeModelVersionTag\n$propertiesTag\n$newProperty$closePropertiesTag")
        }
      }
    pomXml.bufferedWriter().use { it.write(text) }
    return this
  }

  fun downloadArtifactFromMavenCentral(data: MavenArchetypeInfo, repoPath: Path) {
    try {
      listOf("pom", "jar").forEach {
        val fileName = "${data.artefactId}-${data.version}.$it"
        val filePath = "${data.groupId.replace('.', '/')}/${data.artefactId}/${data.version}"
        val file = repoPath.resolve(filePath).findOrCreateDirectory().resolve(fileName).createFile()
        val url = "https://repo1.maven.org/maven2/$filePath/$fileName"
        BufferedInputStream(URL(url).openStream()).use { inputStream ->
          FileOutputStream(file.toString()).use { outputStream ->
            inputStream.copyTo(outputStream)
          }
        }
      }
    }
    catch (e: Exception) {
      throw IllegalStateException("Error downloading artifact: ${e.message}")
    }
  }
}