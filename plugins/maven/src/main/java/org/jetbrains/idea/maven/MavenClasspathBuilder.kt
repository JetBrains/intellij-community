// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven

import com.intellij.ide.plugins.getPluginDistDirByClass
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtil
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.server.MavenServer
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.server.MavenServerManager.Companion.getInstance
import java.nio.file.Files
import java.nio.file.Path

object MavenClasspathBuilder {

  @JvmStatic
  fun addDir(classpath: MutableList<Path>, dir: Path, filter: (Path) -> Boolean) {
    val files = NioFiles.list(dir)

    for (jar in files) {
      if (Files.isRegularFile(jar) && jar.getFileName().toString().endsWith(".jar") && filter(jar)) {
        classpath.add(jar)
      }
    }
  }

  @JvmStatic
  fun addMavenLibs(classpath: MutableList<Path>, mavenHome: Path) {
    addDir(classpath, mavenHome.resolve("lib")) { f -> !f.fileName.toString().contains("maven-slf4j-provider") }
    val bootFolder = mavenHome.resolve("boot")
    val classworldsJars =
      NioFiles.list(bootFolder).stream().filter { f: Path? -> StringUtil.contains(f!!.getFileName().toString(), "classworlds") }.toList()
    classpath.addAll(classworldsJars)
  }

  @JvmStatic
  fun addMavenServerLibraries(classpath: MutableList<Path>, vararg subdirs: String) {

    val pluginDir = getPluginDistDirByClass(MavenServerManager::class.java)
    checkNotNull(pluginDir) { "Cannot resolve Maven plugin directory" }
    val libDir = pluginDir.resolve("lib")

    classpath.add(PathManager.getJarForClass(MavenId::class.java)!!)
    classpath.add(PathManager.getJarForClass(MavenServer::class.java)!!)
    classpath.add(getInstance().getMavenEventListenerPath())
    classpath.add(PathManager.getJarForClass(SpanExporter::class.java)!!)
    subdirs.forEach { addDir(classpath, libDir.resolve(it)) { true } }

  }


}
