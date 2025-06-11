// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.google.common.base.Charsets
import com.intellij.application.options.PathMacrosImpl
import com.intellij.diagnostic.PluginException
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.util.registry.Registry.Companion.stringValue
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.PathUtil
import org.jdom.Element
import org.jetbrains.annotations.NotNull
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.MavenUtil.locateModuleOutput
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

class MavenIndexerCMDState(
  private val myJdk: Sdk,
  private val myOptions: String,
  private val myDistribution: MavenDistribution,
  private val myDebugPort: Int?,
) : CommandLineState(null) {
  @Throws(ExecutionException::class)
  override fun startProcess(): ProcessHandler {
    val params = createJavaParameters()
    val commandLine = params.toCommandLine()
    val processHandler: OSProcessHandler = OSProcessHandler.Silent(commandLine)
    processHandler.setShouldDestroyProcessRecursively(false)
    return processHandler
  }

  protected fun createJavaParameters(): SimpleJavaParameters {
    val params = SimpleJavaParameters()

    params.jdk = myJdk
    params.workingDirectory = PathManager.getBinPath()
    params.vmParametersList.add(stringValue("maven.dedicated.indexer.vmargs"))
    if (myDebugPort != null) {
      params.vmParametersList
        .addParametersString("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=*:$myDebugPort")
    }
    params.vmParametersList.add("-Didea.version=" + MavenUtil.getIdeaVersionToPassToMavenProcess())
    params.mainClass = "org.jetbrains.idea.maven.server.indexer.MavenServerIndexerMain"
    params.classPath.add(PathUtil.getJarPathForClass(StringUtilRt::class.java)) //util-rt
    params.classPath.add(PathUtil.getJarPathForClass(NotNull::class.java)) //annotations-java5
    params.classPath.addAllFiles(collectClassPathAndLibsFolder(myDistribution).map { it.toFile() })
    params.classPath.add(PathUtil.getJarPathForClass(Element::class.java)) //JDOM
    return params
  }

  companion object {

    private fun addDependenciesFromMavenRepo(classPath: MutableList<Path>) {

      val communityHomePath = PathManager.getCommunityHomePath()
      val mavenIndexerIml = Paths.get(communityHomePath).resolve("plugins/maven/maven-server-indexer/intellij.maven.server.indexer.iml")


      val dependenciesUrls = parseDependenciesUrls(mavenIndexerIml) ?: throw RuntimeException("Cannot parse maven.server.indexer.iml")
      val pathMacros = PathMacros.getInstance()
      val pathToRepo = pathMacros.getValue(PathMacrosImpl.MAVEN_REPOSITORY)
        ?.replace('\\', '/')
        ?.let {
        if (FileUtil.isAbsolute(it) && SystemInfo.isWindows) {
          // absolute paths on windows (e.g. 'C:/dir') require additional '`'/` char in the front to be a valid file:// URI.
          "/$it"
        } else {
          it
        }
      } ?: throw RuntimeException("Cannot find maven repo")

      for (depTemplate in dependenciesUrls) {
        val url = depTemplate.replace('$' + PathMacrosImpl.MAVEN_REPOSITORY + '$', pathToRepo)
        val jar = Paths.get(URI(url));
        check(jar.isRegularFile()) { "File $jar not found" }
        classPath.add(jar)
      }
    }

    private fun parseDependenciesUrls(pathToIml: Path): List<String>? {
      val root = JDOMUtil.load(pathToIml) ?: return null
      return root.getChild("component")
               ?.children
               ?.filter { it.name == "orderEntry" && it.getAttributeValue("type") == "module-library" }
               ?.mapNotNull { it.getChild("library")?.getChild("properties")?.getChild("verification") }
               ?.flatMap { it.getChildren("artifact") }
               ?.mapNotNull { it.getAttributeValue("url") } ?: return null

    }

    private fun collectClassPathAndLibsFolder(distribution: MavenDistribution): List<Path> {
      val classpath = ArrayList<Path>()
      addMavenLibs(classpath, distribution.mavenHome)
      addIndexerRTLibs(classpath)
      val pathToClass = PathManager.getJarForClass(MavenServerManager::class.java)
                        ?: throw IllegalStateException("Cannot find path to maven server manager code")

      if (MavenUtil.isRunningFromSources()) {
        // we are running from some kind of sources build, packed or not.
        MavenLog.LOG.debug("collecting classpath for local run")
        prepareClassPathForLocalRunAndUnitTests(classpath)
        addDependenciesFromMavenRepo(classpath)
      } else {
        // we are running in production
        MavenLog.LOG.debug("collecting classpath for production")
        prepareClassPathForProduction(distribution.version!!, classpath, pathToClass.parent)
      }
      MavenLog.LOG.debug("Collected classpath = ", classpath)
      return classpath
    }

    private fun addIndexerRTLibs(classpath: MutableList<Path>) {
      val resources = indexerRTList
      val libDir = MavenUtil.getPluginSystemDir("rt-maven-indexer-lib")
      if (!libDir.isDirectory()) {
        try {
          libDir.createDirectories()
        } catch (e: IOException) {
          throw PluginException("Cannot create cache directory for maven", e, PluginId.getId(MavenUtil.INTELLIJ_PLUGIN_ID))
        }
      }
      for (jarName in resources) {
        val file = libDir.resolve(jarName)
        if (!file.isRegularFile()) {
          try {
            MavenIndexerCMDState::class.java.classLoader
              .getResourceAsStream(jarName).use {
                if (it == null) {
                  throw PluginException("Cannot find runtime library $jarName in resources",
                                        PluginId.getId(MavenUtil.INTELLIJ_PLUGIN_ID))
                }
                Files.copy(it, file)
              }
          }
          catch (e: IOException) {
            throw PluginException("Cannot copy runtime library $jarName into $file", e,
                                  PluginId.getId(MavenUtil.INTELLIJ_PLUGIN_ID))
          }
        }
        classpath.add(file)
      }
    }

    private val indexerRTList: List<String>
      get() {
        try {
          MavenIndexerCMDState::class.java.classLoader
            .getResourceAsStream("META-INF/org.jetbrains.idea.maven.maven-indexer-api-rt").use { `is` ->
              if (`is` == null) throw PluginException("Cannot find indexer rt libs", PluginId.findId(MavenUtil.INTELLIJ_PLUGIN_ID))
              return Arrays.asList(*StreamUtil.readText(InputStreamReader(`is`, Charsets.UTF_8)).split(
                "\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
            }
        }
        catch (e: IOException) {
          throw PluginException(e, PluginId.getId(MavenUtil.INTELLIJ_PLUGIN_ID))
        }
      }

    private fun prepareClassPathForProduction(
      mavenVersion: String,
      classpath: MutableList<Path>,
      root: Path,
    ) {
      classpath.add(PathManager.getJarForClass(MavenId::class.java)!!)
      classpath.add(PathManager.getJarForClass(MavenServer::class.java)!!)
      classpath.add( root.resolve("maven-server-indexer.jar"))
      addDir(classpath, root.resolve("maven-server-indexer"))
      addDir(classpath, root.resolve("intellij.maven.server.indexer").resolve("lib"))
    }

    private fun prepareClassPathForLocalRunAndUnitTests(classpath: MutableList<Path>) {
      classpath.add(PathManager.getJarForClass(MavenId::class.java)!!)
      classpath.add(locateModuleOutput("intellij.maven.server")!!)
      classpath.add(locateModuleOutput("intellij.maven.server.indexer")!!)
    }

    private fun addMavenLibs(classpath: MutableList<Path>, mavenHome: Path) {
      addDir(classpath, mavenHome.resolve("lib"))
      val bootFolder = mavenHome.resolve("boot")
      classpath.addAll(bootFolder.listDirectoryEntries("*classworlds*.jar"))
    }

    private fun addDir(classpath: MutableList<Path>, dir: Path) {
      dir.listDirectoryEntries("*.jar").forEach {
        classpath.add(it)
      }
    }
  }
}
