// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.groovy.wizard

import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logArtifactIdChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logGroupIdChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logParentChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkFinished
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logVersionChanged
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.wizard.GitNewProjectWizardData.Companion.gitData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.name
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.path
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.chain
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.distribution.DistributionInfo
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.dsl.builder.*
import com.intellij.util.castSafelyTo
import com.intellij.util.download.DownloadableFileSetVersions
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardStep
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.config.loadLatestGroovyVersions
import org.jetbrains.plugins.groovy.config.wizard.*
import java.awt.Component
import javax.swing.ComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.SwingUtilities

class MavenGroovyNewProjectWizard : BuildSystemGroovyNewProjectWizard {
  override val name = MAVEN

  override val ordinal: Int = 1

  override fun createStep(parent: GroovyNewProjectWizard.Step) = Step(parent).chain(::AssetsStep)

  class Step(parent: GroovyNewProjectWizard.Step) :
    MavenNewProjectWizardStep<GroovyNewProjectWizard.Step>(parent),
    BuildSystemGroovyNewProjectWizardData by parent {

    private val addSampleCodeProperty = propertyGraph.property(false)

    var addSampleCode by addSampleCodeProperty

    override fun setupSettingsUI(builder: Panel) {
      super.setupSettingsUI(builder)
      builder.row(GroovyBundle.message("label.groovy.sdk")) {
        mavenGroovySdkComboBox(groovySdkProperty)
      }.bottomGap(BottomGap.SMALL)
      builder.addSampleCodeCheckbox(addSampleCodeProperty)
    }

    override fun setupProject(project: Project) {
      val builder = MavenGroovyNewProjectBuilder(groovySdk.getVersion() ?: GROOVY_SDK_FALLBACK_VERSION).apply {
        moduleJdk = sdk
        name = parentStep.name
        parentProject = parentData
        contentEntryPath = "${parentStep.path}/${parentStep.name}"
        aggregatorProject = parentData
        projectId = MavenId(groupId, artifactId, version)
        isInheritGroupId = parentData?.mavenId?.groupId == groupId
        isInheritVersion = parentData?.mavenId?.version == version
        createSampleCode = addSampleCode
      }

      ExternalProjectsManagerImpl.setupCreatedProject(project)
      builder.commit(project)

      logSdkFinished(sdk)
    }

    init {
      sdkProperty.afterChange { logSdkChanged(it) }
      parentProperty.afterChange { logParentChanged(!it.isPresent) }
      addSampleCodeProperty.afterChange { logAddSampleCodeChanged() }
      groupIdProperty.afterChange { logGroupIdChanged() }
      artifactIdProperty.afterChange { logArtifactIdChanged() }
      versionProperty.afterChange { logVersionChanged() }
    }

    private fun Row.mavenGroovySdkComboBox(property: ObservableMutableProperty<DistributionInfo?>) {
      comboBox(getInitializedModel(), fallbackAwareRenderer)
        .columns(COLUMNS_MEDIUM)
        .bindItem(property)
        .validationOnInput {
          if (property.get() == null) {
            warning(GroovyBundle.message("new.project.wizard.groovy.retrieving.has.failed"))
          }
          else {
            null
          }
        }
    }
    private val fallbackAwareRenderer: DefaultListCellRenderer = object : DefaultListCellRenderer() {
      override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
        val representation = value.castSafelyTo<DistributionInfo>()?.getVersion() ?: GROOVY_SDK_FALLBACK_VERSION // NON-NLS
        return super.getListCellRendererComponent(list, representation, index, isSelected, cellHasFocus)
      }
    }

    private fun getInitializedModel(): ComboBoxModel<DistributionInfo?> {
      val model = CollectionComboBoxModel<DistributionInfo?>()
      loadLatestGroovyVersions(object : DownloadableFileSetVersions.FileSetVersionsCallback<FrameworkLibraryVersion>() {
        override fun onSuccess(versions: MutableList<out FrameworkLibraryVersion>) {
          SwingUtilities.invokeLater {
            for (version in versions.sortedWith(::moveUnstableVersionToTheEnd)) {
              model.add(FrameworkLibraryDistributionInfo(version))
            }
            model.selectedItem = model.items.first()
          }
        }

        override fun onError(errorMessage: String) {
          model.add(null)
          model.selectedItem = model.items.first()
        }
      })
      return model
    }

  }

  companion object {
    @JvmField
    val MAVEN = MavenUtil.SYSTEM_ID.readableName
  }

  private class AssetsStep(parent: NewProjectWizardStep) : AssetsNewProjectWizardStep(parent) {
    override fun setupAssets(project: Project) {
      outputDirectory = "$path/$name"
      if (gitData?.git == true) {
        addAssets(StandardAssetsProvider().getMavenIgnoreAssets())
      }
    }
  }
}

private fun DistributionInfo?.getVersion() = when(this) {
  is FrameworkLibraryDistributionInfo -> this.version.versionString
  null -> null
  else -> {
    logger<MavenGroovyNewProjectWizard>().error("Unexpected distribution type")
    null
  }
}