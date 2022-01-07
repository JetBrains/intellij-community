// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.groovy.wizard

import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.columns
import com.intellij.util.download.DownloadableFileSetVersions.FileSetVersionsCallback
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardStep
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.config.loadLatestGroovyVersions
import org.jetbrains.plugins.groovy.config.wizard.BuildSystemGroovyNewProjectWizard
import org.jetbrains.plugins.groovy.config.wizard.GroovyNewProjectWizard
import javax.swing.ComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.SwingUtilities

class MavenGroovyNewProjectWizard : BuildSystemGroovyNewProjectWizard {
  override val name = MAVEN

  override fun createStep(parent: GroovyNewProjectWizard.Step) = Step(parent)

  class Step(parent: GroovyNewProjectWizard.Step) : MavenNewProjectWizardStep<GroovyNewProjectWizard.Step>(parent) {

    private val groovySdkVersionProperty = propertyGraph.graphProperty { FALLBACK_VERSION }

    private var groovySdkVersion by groovySdkVersionProperty

    override fun setupSettingsUI(builder: Panel) {
      super.setupSettingsUI(builder)
      with(builder) {
        row(GroovyBundle.message("label.groovy.sdk")) {
          comboBox(getInitializedModel(), DefaultListCellRenderer())
            .columns(COLUMNS_MEDIUM)
            .bindItem(groovySdkVersionProperty)
        }
      }
    }

    override fun setupProject(project: Project) {
      val builder = MavenGroovyNewProjectBuilder(groovySdkVersion).apply {
        moduleJdk = sdk
        name = parentStep.name
        parentProject = parentData
        contentEntryPath = parentStep.projectPath.systemIndependentPath
        aggregatorProject = parentData
        projectId = MavenId(groupId, artifactId, version)
        isInheritGroupId = parentData?.mavenId?.groupId == groupId
        isInheritVersion = parentData?.mavenId?.version == version
      }

      ExternalProjectsManagerImpl.setupCreatedProject(project)
      builder.commit(project)
    }
  }

  companion object {
    @JvmField
    val MAVEN = MavenUtil.SYSTEM_ID.readableName

    private const val FALLBACK_VERSION = "3.0.9"

    private fun getInitializedModel() : ComboBoxModel<String> {
      val model = CollectionComboBoxModel<String>()
      loadLatestGroovyVersions(object : FileSetVersionsCallback<FrameworkLibraryVersion>() {
        override fun onSuccess(versions: MutableList<out FrameworkLibraryVersion>) {
          SwingUtilities.invokeLater {
            for (version in versions) {
              model.add(version.versionString)
            }
            model.selectedItem = model.items.first()
          }
        }

        override fun onError(errorMessage: String) {
          model.add(FALLBACK_VERSION)
          model.selectedItem = model.items.first()
        }
      })
      return model
    }
  }
}