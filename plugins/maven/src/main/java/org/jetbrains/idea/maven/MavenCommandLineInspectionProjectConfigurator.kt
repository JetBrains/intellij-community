// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven

import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.ide.CommandLineInspectionProjectConfigurator.ConfiguratorContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenArtifactUtil
import org.jetbrains.idea.maven.utils.MavenUtil
import kotlin.io.path.pathString

private const val MAVEN_CREATE_DUMMY_MODULE_ON_FIRST_IMPORT_REGISTRY_KEY = "maven.create.dummy.module.on.first.import"
private val LOG = Logger.getInstance(MavenCommandLineInspectionProjectConfigurator::class.java)
private const val DISABLE_MAVEN_AUTO_IMPORT = "external.system.auto.import.disabled"

class MavenCommandLineInspectionProjectConfigurator : CommandLineInspectionProjectConfigurator {
  override fun getName(): String = "maven"

  override fun getDescription(): String = MavenProjectBundle.message("maven.commandline.description")

  override fun configureEnvironment(context: ConfiguratorContext) = context.run {
    Registry.get(DISABLE_MAVEN_AUTO_IMPORT).setValue(true)
    Registry.get(MAVEN_CREATE_DUMMY_MODULE_ON_FIRST_IMPORT_REGISTRY_KEY).setValue(false)
  }

  override fun configureProject(project: Project, context: ConfiguratorContext) {
    val basePath = context.projectPath.pathString
    val pomXmlFile = basePath + "/" + MavenConstants.POM_XML
    if (FileUtil.findFirstThatExist(pomXmlFile) == null) return

    val mavenProjectAware = ExternalSystemUnlinkedProjectAware.getInstance(MavenUtil.SYSTEM_ID)!!

    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    val isMavenProjectLinked = mavenProjectAware.isLinkedProject(project, basePath)

    LOG.info("maven project: ${project.name} is linked: $isMavenProjectLinked")

    if (!isMavenProjectLinked) {
      ApplicationManager.getApplication().invokeAndWait {
        mavenProjectAware.linkAndLoadProject(project, basePath)
        mavenProjectsManager.waitForResolvingCompletion()
        mavenProjectsManager.waitForPluginsResolvingCompletion()
      }
    }

    val mavenProjectsTree = mavenProjectsManager.projectsTreeForTests
    for (mavenProject in mavenProjectsTree.projects) {
      val hasReadingProblems = mavenProject.hasReadingProblems()
      if (hasReadingProblems) {
        throw IllegalStateException("Maven project ${mavenProject.name} has import problems:" + mavenProject.problems)
      }

      val hasUnresolvedArtifacts = mavenProject.hasUnresolvedArtifacts()
      if (hasUnresolvedArtifacts) {
        val unresolvedArtifacts = mavenProject.dependencies.filterNot { it.isResolved } +
                                  mavenProject.externalAnnotationProcessors.filterNot { it.isResolved }
        throw IllegalStateException("Maven project ${mavenProject.name} has unresolved artifacts: $unresolvedArtifacts")
      }

      val hasUnresolvedPlugins = mavenProject.hasUnresolvedPlugins()
      if (hasUnresolvedPlugins) {
        val unresolvedPlugins = mavenProject.declaredPlugins.filterNot { plugin ->
          MavenArtifactUtil.hasArtifactFile(mavenProject.localRepository, plugin.mavenId)
        }
        throw IllegalStateException("maven project: ${mavenProject.name} has unresolved plugins: $unresolvedPlugins")
      }
    }
  }
}