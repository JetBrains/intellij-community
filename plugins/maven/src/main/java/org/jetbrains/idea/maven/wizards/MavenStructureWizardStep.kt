// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ui.DataView
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.SortedComboBoxModel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import icons.OpenapiIcons
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.util.function.Function
import javax.swing.Icon
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * Truncated implementation of a wizard step for Trantor plugin.
 * Https://plugins.jetbrains.com/plugin/18960-trantor
 */
@Deprecated("Use [MavenNewProjectWizardStep] instead")
@ApiStatus.ScheduledForRemoval
class MavenStructureWizardStep(
  @Suppress("UNUSED_PARAMETER") builder: AbstractMavenModuleBuilder,
  private val context: WizardContext
) : ModuleWizardStep() {

  private val propertyGraph = PropertyGraph()
  private val parentProperty = propertyGraph.property<DataView<MavenProject>>(EMPTY_VIEW)

  var parent by parentProperty

  override fun getComponent(): DialogPanel {
    return panel {
      row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.parent.label")) {
        val presentationName = Function<DataView<MavenProject>, String> { it.presentationName }
        val parentComboBoxModel = SortedComboBoxModel(Comparator.comparing(presentationName, String.CASE_INSENSITIVE_ORDER))
        parentComboBoxModel.add(EMPTY_VIEW)
        parentComboBoxModel.addAll(findAllParents())
        comboBox(parentComboBoxModel, renderer = getParentRenderer())
          .bindItem(parentProperty)
      }
    }.apply {
      registerValidators(context.disposable)
    }
  }

  private fun findAllParents(): List<MavenDataView> {
    val project = context.project ?: return emptyList()
    val projectsManager = MavenProjectsManager.getInstance(project)
    return projectsManager.projects.map { MavenDataView(it) }
  }

  private fun getParentRenderer(): ListCellRenderer<DataView<MavenProject>?> {
    return object : SimpleListCellRenderer<DataView<MavenProject>?>() {
      override fun customize(list: JList<out DataView<MavenProject>?>,
                             value: DataView<MavenProject>?,
                             index: Int,
                             selected: Boolean,
                             hasFocus: Boolean) {
        val view = value ?: EMPTY_VIEW
        text = view.presentationName
        icon = DataView.getIcon(view)
      }
    }
  }

  override fun updateDataModel() {}

  class MavenDataView(override val data: MavenProject) : DataView<MavenProject>() {
    override val location: String = data.directory
    override val icon: Icon = OpenapiIcons.RepositoryLibraryLogo
    override val presentationName: String = data.displayName
    override val groupId: String = data.mavenId.groupId ?: ""
    override val version: String = data.mavenId.version ?: ""
  }

  companion object {
    private val EMPTY_VIEW = object : DataView<Nothing>() {
      override val data: Nothing get() = throw UnsupportedOperationException()
      override val location: String = ""
      override val icon: Nothing get() = throw UnsupportedOperationException()
      override val presentationName: String = "<None>"
      override val groupId: String = "org.example"
      override val version: String = "1.0-SNAPSHOT"

      override val isPresent: Boolean = false
    }
  }
}