// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MavenFilterConfigCrc")

package com.intellij.maven.jps.ide.compilation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Comparing
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import org.jetbrains.idea.maven.dom.references.MavenFilteredPropertyPsiReferenceProvider
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.project.MavenProjectsTree.Companion.getFilterExclusions
import org.jetbrains.idea.maven.utils.MavenLog
import java.io.File
import java.io.IOException
import java.io.Writer
import java.util.zip.CRC32

fun MavenProjectsTree.getFilterConfigCrc(fileIndex: ProjectFileIndex, profiles: MavenExplicitProfiles): Int {
  ApplicationManager.getApplication().assertReadAccessAllowed()

  val crc = CRC32()
  updateCrc(crc, profiles.hashCode())

  val allProjects = this.projects

  crc.update(allProjects.size and 0xFF)
  for (mavenProject in allProjects) {
    val pomFile = mavenProject.file
    val module = fileIndex.getModuleForFile(pomFile) ?: continue

    if (!Comparing.equal(fileIndex.getContentRootForFile(pomFile), pomFile.parent)) continue

    updateCrc(crc, module.name)

    val mavenId = mavenProject.mavenId
    updateCrc(crc, mavenId.groupId)
    updateCrc(crc, mavenId.artifactId)
    updateCrc(crc, mavenId.version)

    val parentId = mavenProject.parentId
    if (parentId != null) {
      updateCrc(crc, parentId.groupId)
      updateCrc(crc, parentId.artifactId)
      updateCrc(crc, parentId.version)
    }

    updateCrc(crc, mavenProject.directory)
    updateCrc(crc, MavenFilteredPropertyPsiReferenceProvider.getDelimitersPattern(mavenProject).pattern())
    updateCrc(crc, mavenProject.modelMap.hashCode())
    updateCrc(crc, mavenProject.resources.hashCode())
    updateCrc(crc, mavenProject.testResources.hashCode())
    updateCrc(crc, getFilterExclusions(mavenProject).hashCode())
    updateCrc(crc, mavenProject.properties.hashCode())

    for (each in mavenProject.filterPropertiesFiles) {
      val file = File(each)
      updateCrc(crc, file.lastModified())
    }

    val outputter = XMLOutputter(Format.getCompactFormat())

    val crcWriter: Writer = object : Writer() {
      override fun write(cbuf: CharArray, off: Int, len: Int) {
        var i = off
        val end = off + len
        while (i < end) {
          crc.update(cbuf[i].code)
          i++
        }
      }

      override fun flush() {
      }

      override fun close() {
      }
    }

    try {
      val resourcePluginCfg = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-resources-plugin")
      if (resourcePluginCfg != null) {
        outputter.output(resourcePluginCfg, crcWriter)
      }

      val warPluginCfg = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-war-plugin")
      if (warPluginCfg != null) {
        outputter.output(warPluginCfg, crcWriter)
      }
    }
    catch (e: IOException) {
      MavenLog.LOG.error(e)
    }
  }
  return crc.value.toInt()
}

private fun updateCrc(crc: CRC32, xInt: Int) {
  var x = xInt
  crc.update(x and 0xFF)
  x = x ushr 8
  crc.update(x and 0xFF)
  x = x ushr 8
  crc.update(x and 0xFF)
  x = x ushr 8
  crc.update(x)
}

private fun updateCrc(crc: CRC32, l: Long) {
  updateCrc(crc, l.toInt())
  updateCrc(crc, (l ushr 32).toInt())
}

private fun updateCrc(crc: CRC32, s: String?) {
  if (s == null) {
    crc.update(111)
  }
  else {
    updateCrc(crc, s.hashCode())
    crc.update(s.length and 0xFF)
  }
}
