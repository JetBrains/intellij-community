// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.idea.maven.indices.IndicesBundle
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenId
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*
import java.util.zip.ZipFile

@Obsolete
fun MavenArtifact.resolved() = isResolved

object MavenArtifactUtil {
  @JvmField
  val DEFAULT_GROUPS: Array<String> = arrayOf("org.apache.maven.plugins", "org.codehaus.mojo")
  const val MAVEN_PLUGIN_DESCRIPTOR: String = "META-INF/maven/plugin.xml"

  private val ourPluginInfoCache: MutableMap<Path, MavenPluginInfo?> = Collections.synchronizedMap(HashMap())

  @JvmStatic
  @Deprecated("this method does not support split repositories")
  fun readPluginInfo(localRepository: File, mavenId: MavenId): MavenPluginInfo? {
    return readPluginInfo(localRepository.toPath(), mavenId)
  }

  @JvmStatic
  @Deprecated("this method does not support split repositories")
  fun readPluginInfo(localRepository: Path, mavenId: MavenId): MavenPluginInfo? {
    val file = getArtifactNioPath(localRepository, mavenId.groupId, mavenId.artifactId, mavenId.version, "jar")
    return readPluginInfo(file)
  }

  @JvmStatic
  fun readPluginInfo(mavenArtifact: MavenArtifact?): MavenPluginInfo? {
    val file = mavenArtifact?.file?.toPath() ?: return null
    return readPluginInfo(file)
  }

  @JvmStatic
  fun readPluginInfo(file: Path): MavenPluginInfo? {
    var result = ourPluginInfoCache[file]
    if (result == null) {
      result = createPluginDocument(file)
      ourPluginInfoCache[file] = result
    }
    return result
  }

  @JvmStatic
  @JvmOverloads
  @Deprecated("this method does not support split repositories")
  internal fun hasArtifactFile(localRepository: Path, id: MavenId, type: String = "jar"): Boolean {
    return Files.exists(getArtifactFile(localRepository, id, type))
  }

  @JvmStatic
  @Deprecated("this method does not support split repositories")
  fun getArtifactFile(localRepository: File, id: MavenId, type: String): Path {
    return getArtifactNioPath(localRepository.toPath(), id.groupId, id.artifactId, id.version, type)
  }

  @JvmStatic
  @Deprecated("this method does not support split repositories")
  fun getArtifactFile(localRepository: Path, id: MavenId, type: String): Path {
    return getArtifactNioPath(localRepository, id.groupId, id.artifactId, id.version, type)
  }

  @JvmStatic
  @Deprecated("this method does not support split repositories")
  fun getArtifactFile(localRepository: Path, id: MavenId): Path {
    return getArtifactNioPath(localRepository, id.groupId, id.artifactId, id.version, "pom")
  }

  @JvmStatic
  fun isPluginIdEquals(groupId1: String?, artifactId1: String?, groupId2: String?, artifactId2: String?): Boolean {
    var groupId1 = groupId1
    var groupId2 = groupId2
    if (artifactId1 == null) return false

    if (artifactId1 != artifactId2) return false

    if (groupId1 != null) {
      for (group in DEFAULT_GROUPS) {
        if (groupId1 == group) {
          groupId1 = null
          break
        }
      }
    }

    if (groupId2 != null) {
      for (group in DEFAULT_GROUPS) {
        if (groupId2 == group) {
          groupId2 = null
          break
        }
      }
    }

    return groupId1 == groupId2
  }

  @JvmStatic
  @Deprecated("this method does not support split repositories")
  fun getArtifactNioPath(localRepository: Path, groupId: String?, artifactId: String?, version: String?, type: String): Path {
    var groupId = groupId
    var artifactId = artifactId
    var version = version
    groupId = sanitizeMavenIdentifier(groupId)
    artifactId = sanitizeMavenIdentifier(artifactId)
    version = sanitizeMavenIdentifier(version)
    var dir: Path? = null
    if (StringUtil.isEmpty(groupId)) {
      for (each in DEFAULT_GROUPS) {
        dir = getArtifactDirectory(localRepository, each, artifactId)
        if (Files.exists(dir)) break
      }
    }
    else {
      dir = getArtifactDirectory(localRepository, groupId, artifactId)
    }

    if (StringUtil.isEmpty(version)) version = resolveVersion(dir!!)
    return dir!!.resolve(version).resolve("$artifactId-$version.$type")
  }

  private fun sanitizeMavenIdentifier(groupOrArtifactId: String?): String {
    if (null == groupOrArtifactId) return ""
    val result = StringBuilder(groupOrArtifactId.length)
    for (c in groupOrArtifactId) {
      if (Character.isLetterOrDigit(c) || c == '-' || c == '.' || c == '_') {
        result.append(c)
      }
    }
    return result.toString()
  }

  private fun getArtifactDirectory(localRepository: Path, groupId: String, artifactId: String): Path {
    var groupId = groupId
    var artifactId = artifactId
    groupId = sanitizeMavenIdentifier(groupId)
    artifactId = sanitizeMavenIdentifier(artifactId)
    return localRepository.resolve(StringUtil.replace(groupId, ".", File.separator)).resolve(artifactId)
  }

  private fun resolveVersion(pluginDir: Path): String {
    val versions: MutableList<String> = ArrayList()
    try {
      Files.list(pluginDir).use { children ->
        children.forEach { path: Path ->
          if (Files.isDirectory(path)) {
            versions.add(path.fileName.toString())
          }
        }
      }
    }
    catch (e: NoSuchFileException) {
      return ""
    }
    catch (e: Exception) {
      MavenLog.LOG.warn(e.message)
      return ""
    }

    if (versions.isEmpty()) return ""

    versions.sort()
    return versions[versions.size - 1]
  }

  private fun createPluginDocument(file: Path): MavenPluginInfo? {
    try {
      if (!Files.exists(file)) return null

      ZipFile(file.toFile()).use { jar ->
        val entry = jar.getEntry(MAVEN_PLUGIN_DESCRIPTOR)
        if (entry == null) {
          MavenLog.LOG.info(IndicesBundle.message("repository.plugin.corrupt", file))
          return null
        }
        jar.getInputStream(entry).use { `is` ->
          val bytes = FileUtil.loadBytes(`is`)
          return MavenPluginInfo(bytes)
        }
      }
    }
    catch (e: IOException) {
      MavenLog.LOG.info(e)
      return null
    }
  }
}
