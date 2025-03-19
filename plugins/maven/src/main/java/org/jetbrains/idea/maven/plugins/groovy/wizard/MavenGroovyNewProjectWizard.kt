// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.groovy.wizard

import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeFinished
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.MAVEN
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.ADD_SAMPLE_CODE_PROPERTY_NAME
import com.intellij.openapi.diagnostic.logger
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

  override fun createStep(parent: GroovyNewProjectWizard.Step): NewProjectWizardStep =
    Step(parent)
      .nextStep(::AssetsStep)

  class Step(parent: GroovyNewProjectWizard.Step) :
    MavenNewProjectWizardStep<GroovyNewProjectWizard.Step>(parent),
    BuildSystemGroovyNewProjectWizardData by parent {

    private val addSampleCodeProperty = propertyGraph.property(true)
      .bindBooleanStorage(ADD_SAMPLE_CODE_PROPERTY_NAME)

    var addSampleCode: Boolean by addSampleCodeProperty

    private fun setupGroovySdkUI(builder: Panel) {
      builder.row(GroovyBundle.message("label.groovy.sdk")) {
        comboBox(getInitializedModel(), fallbackAwareRenderer)
          .columns(COLUMNS_MEDIUM)
          .bindItem(groovySdkProperty)
          .validationOnInput { validateGroovySdk(groovySdk) }
          .whenItemSelectedFromUi { logGroovySdkChanged(groovySdk) }
          .onApply { logGroovySdkFinished(groovySdk) }
      }.bottomGap(BottomGap.SMALL)
    }

    private fun setupSampleCodeUI(builder: Panel) {
      builder.row {
        checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
          .bindSelected(addSampleCodeProperty)
          .whenStateChangedFromUi { logAddSampleCodeChanged(it) }
          .onApply { logAddSampleCodeFinished(addSampleCode) }
      }
    }

    override fun setupSettingsUI(builder: Panel) {
      setupJavaSdkUI(builder)
      setupGroovySdkUI(builder)
      setupParentsUI(builder)
      setupSampleCodeUI(builder)
    }

    override fun setupAdvancedSettingsUI(builder: Panel) {
      setupGroupIdUI(builder)
      setupArtifactIdUI(builder)
    }

    override fun setupProject(project: Project) {
      linkMavenProject(project, MavenGroovyNewProjectBuilder()) { builder ->
        groovySdk.getVersion()?.let { groovySdkVersion ->
          builder.groovySdkVersion = groovySdkVersion
        }
      }
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
        override fun onSuccess(versions: List<FrameworkLibraryVersion>) {
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

  private class AssetsStep(private val parent: Step) : AssetsNewProjectWizardStep(parent) {

    override fun setupAssets(project: Project) {
      if (context.isCreatingNewProject) {
        addAssets(StandardAssetsProvider().getMavenIgnoreAssets())
      }

      addEmptyDirectoryAsset("src/main/groovy")
      addEmptyDirectoryAsset("src/main/resources")
      addEmptyDirectoryAsset("src/test/groovy")
      addEmptyDirectoryAsset("src/test/resources")

      if (parent.addSampleCode) {
        withGroovySampleCode("src/main/groovy", parent.groupId)
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