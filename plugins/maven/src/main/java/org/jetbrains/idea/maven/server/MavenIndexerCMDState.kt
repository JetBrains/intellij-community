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
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.util.registry.Registry.Companion.stringValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.PathUtil
import org.jdom.Element
import org.jetbrains.annotations.NotNull
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.*
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.function.Predicate

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
    params.classPath.addAllFiles(collectClassPathAndLibsFolder(myDistribution))
    params.classPath.add(PathUtil.getJarPathForClass(Element::class.java)) //JDOM
    return params
  }

  companion object {

    private fun addDependenciesFromMavenRepo(classPath: MutableList<File>) {

      val communityHomePath = PathManager.getCommunityHomePath()
      val mavenIndexerIml = Paths.get(communityHomePath).resolve("plugins/maven/maven-server-indexer/intellij.maven.server.indexer.iml")


      val dependenciesUrls = parseDependenciesUrls(mavenIndexerIml) ?: throw RuntimeException("Cannot parse maven.server.indexer.iml")
      val pathMacros = PathMacros.getInstance()
      val pathToRepo = pathMacros.getValue(PathMacrosImpl.MAVEN_REPOSITORY) ?: throw RuntimeException("Cannot find maven repo")

      for (depTemplate in dependenciesUrls) {
        val url = depTemplate.replace('$' + PathMacrosImpl.MAVEN_REPOSITORY + '$', pathToRepo)
        val path = Paths.get(URI(url));
        val jar = path.toFile()
        check(jar.isFile) { "File " + jar.path + " not found" }
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

    private fun collectClassPathAndLibsFolder(distribution: MavenDistribution): List<File> {
      val pluginFileOrDir = File(PathUtil.getJarPathForClass(MavenServerManager::class.java))

      val root = pluginFileOrDir.parent

      val classpath: MutableList<File> = ArrayList()

      addMavenLibs(classpath, distribution.mavenHome.toFile())
      addIndexerRTLibs(classpath)
      if (pluginFileOrDir.isDirectory) {
        MavenLog.LOG.debug("collecting classpath for local run")

        prepareClassPathForLocalRunAndUnitTests(distribution.version!!, classpath, root)
        addDependenciesFromMavenRepo(classpath)
      }
      else {
        MavenLog.LOG.debug("collecting classpath for production")
        prepareClassPathForProduction(distribution.version!!, classpath, root)
      }

      MavenLog.LOG.debug("Collected classpath = ", classpath)
      return classpath
    }

    private fun addIndexerRTLibs(classpath: MutableList<File>) {
      val resources = indexerRTList
      val libDir = MavenUtil.getPluginSystemDir("rt-maven-indexer-lib").toFile()
      if (!libDir.isDirectory) {
        if (!libDir.mkdirs()) {
          throw PluginException("Cannot create cache directory for maven", PluginId.getId(MavenUtil.INTELLIJ_PLUGIN_ID))
        }
      }
      for (jarName in resources) {
        val file = File(libDir, jarName)
        if (!file.isFile) {
          try {
            MavenIndexerCMDState::class.java.classLoader
              .getResourceAsStream(jarName).use { `is` ->
                BufferedOutputStream(FileOutputStream(file)).use { bos ->
                  if (`is` == null) {
                    throw PluginException("Cannot find runtime library $jarName in resources",
                                          PluginId.getId(MavenUtil.INTELLIJ_PLUGIN_ID))
                  }
                  StreamUtil.copy(`is`, bos)
                }
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
      classpath: MutableList<File>,
      root: String,
    ) {
      classpath.add(File(PathUtil.getJarPathForClass(MavenId::class.java)))
      classpath.add(File(PathUtil.getJarPathForClass(MavenServer::class.java)))
      classpath.add(File(root, "maven-server-indexer.jar"))
      addDir(classpath, File(root, "maven-server-indexer"))
      addDir(classpath, File(File(root, "intellij.maven.server.indexer"), "lib"))
    }

    private fun prepareClassPathForLocalRunAndUnitTests(mavenVersion: String, classpath: MutableList<File>, root: String) {
      classpath.add(File(PathUtil.getJarPathForClass(MavenId::class.java)))
      classpath.add(File(root, "intellij.maven.server"))
      classpath.add(File(root, "intellij.maven.server.indexer"))
    }

    private fun addMavenLibs(classpath: MutableList<File>, mavenHome: File) {
      addDir(classpath, File(mavenHome, "lib"))
      val bootFolder = File(mavenHome, "boot")
      val classworldsJars = bootFolder.listFiles { dir: File?, name: String? ->
        StringUtil.contains(
          name!!, "classworlds")
      }
      if (classworldsJars != null) {
        Collections.addAll(classpath, *classworldsJars)
      }
    }

    private fun addDir(classpath: MutableList<File>, dir: File) {
      val files = dir.listFiles()
      if (files == null) return

      for (jar in files) {
        if (jar.isFile && jar.name.endsWith(".jar")) {
          classpath.add(jar)
        }
      }
    }
  }
}
