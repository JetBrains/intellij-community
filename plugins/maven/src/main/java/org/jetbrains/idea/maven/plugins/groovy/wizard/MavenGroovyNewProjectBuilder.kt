// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.groovy.wizard

import com.intellij.ide.util.EditorHelper
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.wizards.AbstractMavenModuleBuilder
import org.jetbrains.idea.maven.wizards.MavenStructureWizardStep
import org.jetbrains.idea.maven.wizards.MavenWizardBundle
import org.jetbrains.idea.maven.wizards.SelectPropertiesStep
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.config.wizard.createSampleGroovyCodeFile
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

/**
 * Currently used only for new project wizard, thus the functionality is rather limited
 */
class MavenGroovyNewProjectBuilder(private val groovySdkVersion : String) : AbstractMavenModuleBuilder() {

  var createSampleCode = false

  override fun createWizardSteps(wizardContext: WizardContext, modulesProvider: ModulesProvider): Array<ModuleWizardStep> = arrayOf(
    MavenStructureWizardStep(this, wizardContext),
    SelectPropertiesStep(wizardContext.project, this),
  )

  override fun setupRootModel(rootModel: ModifiableRootModel) {
    val project = rootModel.project

    val contentPath = contentEntryPath ?: return
    val path = FileUtil.toSystemIndependentName(contentPath).also { Path(it).createDirectories() }
    val root = LocalFileSystem.getInstance().refreshAndFindFileByPath(path) ?: return
    rootModel.addContentEntry(root)

    if (myJdk != null) {
      rootModel.sdk = myJdk
    }
    else {
      rootModel.inheritSdk()
    }

    MavenUtil.runWhenInitialized(project) { setupMavenStructure(project, root) }
  }

  private fun setupMavenStructure(project: Project, root: VirtualFile) {
    val pom = WriteCommandAction.writeCommandAction(project)
      .withName(MavenWizardBundle.message("maven.new.project.wizard.groovy.creating.groovy.project"))
      .compute<VirtualFile, RuntimeException> {
        root.findChild(MavenConstants.POM_XML)?.delete(this)
        val file = root.createChildData(this, MavenConstants.POM_XML)
        val properties = Properties()
        val conditions = Properties()
        properties.setProperty("GROOVY_VERSION", groovySdkVersion)
        properties.setProperty("GROOVY_REPOSITORY", GroovyConfigUtils.getMavenSdkRepository(groovySdkVersion))
        conditions.setProperty("NEED_POM", (GroovyConfigUtils.compareSdkVersions(groovySdkVersion, GroovyConfigUtils.GROOVY2_5) >= 0).toString())
        conditions.setProperty("CREATE_SAMPLE_CODE", "true")
        MavenUtil.runOrApplyMavenProjectFileTemplate(project, file, projectId, null, null, properties,
                                                     conditions, MAVEN_GROOVY_XML_TEMPLATE, false)
        file
      }

    val sourceDirectory = VfsUtil.createDirectories(root.path + "/src/main/groovy")
    VfsUtil.createDirectories(root.path + "/src/main/resources")
    VfsUtil.createDirectories(root.path + "/src/test/groovy")
    if (createSampleCode) {
      createSampleGroovyCodeFile(project, sourceDirectory)
    }

    MavenProjectsManager.getInstance(project).forceUpdateAllProjectsOrFindAllAvailablePomFiles()

    MavenUtil.invokeLater(project, ModalityState.NON_MODAL) {
      PsiManager.getInstance(project).findFile(pom)?.let(EditorHelper::openInEditor)
    }
  }
}

private const val MAVEN_GROOVY_XML_TEMPLATE = "Maven Groovy.xml"