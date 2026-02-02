// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.util.xml.DomFileElement
import com.intellij.util.xml.highlighting.BasicDomElementsInspection
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.MavenWrapper
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.server.isMaven4
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.writeText


private val VERSION_TO_UPDATE_TO = "4.0.0-rc-5"

class MavenNewModelVersionInOldMavenInspection : BasicDomElementsInspection<MavenDomProjectModel?>(MavenDomProjectModel::class.java) {
  override fun getGroupDisplayName(): String {
    return MavenDomBundle.message("inspection.group")
  }

  override fun getDefaultLevel(): HighlightDisplayLevel {
    return HighlightDisplayLevel.ERROR
  }

  override fun checkFileElement(
    domFileElement: DomFileElement<MavenDomProjectModel?>,
    holder: DomElementAnnotationHolder,
  ) {

    val project = domFileElement.file.project
    val psiFile = domFileElement.file
    val vFile = psiFile.virtualFile
    val mavenProject =
      MavenProjectsManager.getInstance(project).findProject(vFile) ?: return

    val rootProject = MavenProjectsManager.getInstance(project).findRootProject(mavenProject) ?: return

    val projectModel = domFileElement.getRootElement()
    if (projectModel.modelVersion.stringValue == MavenConstants.MODEL_VERSION_4_0_0) return

    val distribution = MavenDistributionsCache.getInstance(psiFile.project).getMavenDistribution(psiFile.virtualFile)
    if (distribution.isMaven4()) return

    holder.createProblem(projectModel.modelVersion,
                         HighlightSeverity.ERROR,
                         MavenDomBundle.message("inspection.new.model.version.with.old.maven"),
                         UpdateMavenWrapper(rootProject, VERSION_TO_UPDATE_TO)

    )
  }
}

class UpdateMavenWrapper(@Suppress("ActionIsNotPreviewFriendly") val mavenProject: MavenProject, val version: String) : LocalQuickFix {
  override fun getName(): String {
    return SyncBundle.message("maven.sync.quickfixes.update.maven.version", version)
  }

  override fun getFamilyName(): @IntentionFamilyName String {
    return name
  }

  private fun createTempProject(project: Project): Path {
    val tmp = EelPathUtils.createTemporaryDirectory(project,
                                                    prefix = "mvn-wrapper-update",
                                                    deleteOnExit = true)
    tmp.resolve("pom.xml").writeText(createDummyPomContent())
    return tmp
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {

    val workingDir = mavenProject.directoryFile
    val tempDir = createTempProject(project)
    MavenRunConfigurationType.runConfiguration(project,
                                               MavenRunnerParameters(
                                                 true,
                                                 tempDir.toString(),
                                                 null as String?,
                                                 listOf("wrapper:wrapper"),
                                                 null
                                               ).also { it.cmdOptions = "-N" },
                                               null,
                                               MavenRunnerSettings().also {
                                                 it.setVmOptions("-Dmaven=$version")
                                               }
    ) {
      it.processHandler?.addProcessListener(object : ProcessListener {
        override fun processTerminated(event: ProcessEvent) {
          if (event.exitCode == 0) {
            copyFromTempDir(tempDir, workingDir.toNioPath())
            workingDir.refresh(true, true) {
              MavenWorkspaceSettingsComponent.getInstance(project).settings.generalSettings.mavenHomeType = MavenWrapper
              MavenDistributionsCache.getInstance(project).cleanCaches()
              MavenServerManager.getInstance().shutdownMavenConnectors(project){true}
              MavenProjectsManager.getInstance(project).forceUpdateAllProjectsOrFindAllAvailablePomFiles()
            }

          }
          removeTempDirSafely(tempDir)
        }
      })
    }
  }

  private fun copyFromTempDir(from: Path, to: Path) {
    Files.walk(from).use { stream ->
      stream.forEach { file ->
        val relative = from.relativize(file)
        if (!relative.toString().isEmpty()
            && !file.name.equals("pom.xml", true)) {
          val newFile = to.resolve(relative)
          if (file.isDirectory()) {
            try {
              Files.createDirectory(newFile)
            }
            catch (_: FileAlreadyExistsException) {
            }
          }
          else {
            Files.copy(file, newFile, StandardCopyOption.REPLACE_EXISTING)
          }
        }
      }
    }
  }

  @OptIn(ExperimentalPathApi::class)
  private fun removeTempDirSafely(tempDir: Path) {
    try {
      tempDir.deleteRecursively()
    }
    catch (_: Throwable) {
    }

  }

  private fun createDummyPomContent(): String {
    return """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

<modelVersion>4.0.0</modelVersion>
<groupId>${mavenProject.mavenId.groupId}</groupId>
<artifactId>${mavenProject.mavenId.artifactId}</artifactId>
<version>${mavenProject.mavenId.version}</version>
</project>
    """
  }
}