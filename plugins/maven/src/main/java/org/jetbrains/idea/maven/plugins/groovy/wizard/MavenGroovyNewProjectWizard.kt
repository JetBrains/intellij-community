// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.groovy.wizard

import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.MAVEN
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.name
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.path
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.chain
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.distribution.DistributionInfo
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.asSafely
import com.intellij.util.download.DownloadableFileSetVersions
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectsManager
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

  override val ordinal = 100

  override fun createStep(parent: GroovyNewProjectWizard.Step) = Step(parent).chain(::AssetsStep)

  class Step(parent: GroovyNewProjectWizard.Step) :
    MavenNewProjectWizardStep<GroovyNewProjectWizard.Step>(parent),
    BuildSystemGroovyNewProjectWizardData by parent {

    private val addSampleCodeProperty = propertyGraph.property(true)
      .bindBooleanStorage("NewProjectWizard.addSampleCodeState")

    private var addSampleCode by addSampleCodeProperty

    override fun setupSettingsUI(builder: Panel) {
      super.setupSettingsUI(builder)
      with(builder) {
        row(GroovyBundle.message("label.groovy.sdk")) {
          mavenGroovySdkComboBox(groovySdkProperty)
        }.bottomGap(BottomGap.SMALL)
        row {
          checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
            .bindSelected(addSampleCodeProperty)
            .whenStateChangedFromUi { logAddSampleCodeChanged(it) }
        }.topGap(TopGap.SMALL)
      }
    }

    override fun setupProject(project: Project) {
      super.setupProject(project)

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
      MavenProjectsManager.setupCreatedMavenProject(project)

      project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, true)
      builder.commit(project)
    }

    private fun Row.mavenGroovySdkComboBox(property: ObservableMutableProperty<DistributionInfo?>) {
      comboBox(getInitializedModel(), fallbackAwareRenderer)
        .columns(COLUMNS_MEDIUM)
        .bindItem(property)
        .validationOnInput { validateGroovySdk(property.get()) }
        .whenItemSelectedFromUi { logGroovySdkChanged(context, property.get()) }
    }

    private fun ValidationInfoBuilder.validateGroovySdk(sdk: DistributionInfo?): ValidationInfo? {
      if (sdk == null) {
        return warning(GroovyBundle.message("new.project.wizard.groovy.retrieving.has.failed"))
      }
      return null
    }

    private val fallbackAwareRenderer: DefaultListCellRenderer = object : DefaultListCellRenderer() {
      override fun getListCellRendererComponent(list: JList<*>?,
                                                value: Any?,
                                                index: Int,
                                                isSelected: Boolean,
                                                cellHasFocus: Boolean): Component {
        val representation = value.asSafely<DistributionInfo>()?.getVersion() ?: GROOVY_SDK_FALLBACK_VERSION // NON-NLS
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

  private class AssetsStep(parent: NewProjectWizardStep) : AssetsNewProjectWizardStep(parent) {
    override fun setupAssets(project: Project) {
      outputDirectory = "$path/$name"
      addAssets(StandardAssetsProvider().getMavenIgnoreAssets())
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