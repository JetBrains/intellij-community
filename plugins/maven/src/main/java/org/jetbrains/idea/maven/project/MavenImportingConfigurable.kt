// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.options.BackedByPersistentState
import com.intellij.openapi.options.BoundCompositeSearchableConfigurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import org.jetbrains.annotations.ApiStatus

class MavenImportingConfigurable(private val myProject: Project) :
  BoundCompositeSearchableConfigurable<UnnamedConfigurable>(MavenProjectBundle.message("maven.tab.importing"), SETTINGS_ID),
  BackedByPersistentState {

  private var mySettingsForm: MavenImportingSettingsForm? = null

  @ApiStatus.Internal
  override fun getBackingComponents(): Collection<PersistentStateComponent<*>> {
    return listOf(MavenWorkspaceSettingsComponent.getInstance(myProject))
  }

  override fun createConfigurables(): List<UnnamedConfigurable> {
    return AdditionalMavenImportingSettings.EP_NAME.extensionList.mapNotNull {
      it.createConfigurable(myProject)
    }
  }

  override fun createPanel(): DialogPanel {
    val ui = MavenImportingSettingsUi {
      for (additionalConfigurable in configurables) {
        appendDslConfigurable(additionalConfigurable)
      }
    }
    mySettingsForm = MavenImportingSettingsForm(myProject, disposable!!, ui)

    return mySettingsForm!!.createComponent()
  }

  override fun isModified(): Boolean {
    return super.isModified() || mySettingsForm?.isModified() == true
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    super.apply()
    mySettingsForm?.apply()
    ExternalProjectsManagerImpl.getInstance(myProject).setStoreExternally(true)
  }

  override fun reset() {
    super.reset()
    mySettingsForm?.reset()
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    mySettingsForm = null
  }

  companion object {
    const val SETTINGS_ID: String = "reference.settings.project.maven.importing"
  }
}
