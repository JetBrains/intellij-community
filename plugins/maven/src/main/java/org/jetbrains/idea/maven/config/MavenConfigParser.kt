package org.jetbrains.idea.maven.config

import com.intellij.platform.eel.fs.EelFiles
import com.intellij.util.execution.ParametersListUtil
import org.apache.commons.cli.CommandLineParser
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.utils.MavenLog
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.isRegularFile

object MavenConfigParser {
  @JvmStatic
  fun parse(baseDir: String): MavenConfig? {
    val configFile = Path.of(baseDir, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH)
    if (!configFile.isRegularFile()) return null

    try {
      val content = EelFiles.readString(configFile, Charset.defaultCharset())
      if (content.isBlank()) return null

      val allTokens = ParametersListUtil.parse(content)

      val (javaTokens, mavenTokens) = allTokens.partition { it.startsWith("-D") }

      val options = Options().apply {
        MavenConfigSettings.entries.forEach { addOption(it.toOption()) }
      }

      val parser: CommandLineParser = DefaultParser()
      val commandLine = parser.parse(options, mavenTokens.toTypedArray(), true)

      return MavenConfig(
        commandLine.options.associateBy { it.opt },
        extractJavaProperties(javaTokens),
        baseDir
      )
    } catch (e: Exception) {
      MavenLog.LOG.error("Error parsing maven config at $configFile", e)
    }
    return null
  }

  private fun extractJavaProperties(javaTokens: List<String>): Map<String, String> {
    return javaTokens.associate { token ->
      val pairString = token.removePrefix("-D")
      val eqIndex = pairString.indexOf('=')

      if (eqIndex == -1) {
        pairString to ""
      } else {
        val key = pairString.substring(0, eqIndex)
        val value = pairString.substring(eqIndex + 1)
        key to value.trim('\'', '"')
      }
    }
  }
}