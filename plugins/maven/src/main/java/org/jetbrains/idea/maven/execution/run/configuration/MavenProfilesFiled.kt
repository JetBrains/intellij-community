// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.run.configuration

import com.intellij.execution.configurations.ParametersList
import com.intellij.openapi.externalSystem.service.ui.completion.JTextCompletionContributor
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionPopup
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.observable.properties.AtomicObservableProperty
import com.intellij.openapi.observable.properties.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.layout.*
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.idea.maven.project.MavenProjectsManager

class MavenProfilesFiled(project: Project, workingDirectoryField: WorkingDirectoryField) : ExtendableTextField() {
  private val textProperty = AtomicObservableProperty("")

  var profiles by textProperty.transform(::decodeProfiles, ::encodeProfiles)

  init {
    bind(textProperty)
  }

  private fun decodeProfiles(profiles: String): Map<String, Boolean> {
    val profilesMap = HashMap<String, Boolean>()
    for (profile in ParametersListUtil.parse(profiles)) {
      if (profile.startsWith("-") || profile.startsWith("!")) {
        if (profile.length > 1) {
          profilesMap[profile.substring(1)] = false
        }
      }
      else {
        profilesMap[profile] = true
      }
    }
    return profilesMap
  }

  private fun encodeProfiles(profiles: Map<String, Boolean>): String {
    val parametersList = ParametersList()
    for ((profileName, isEnabled) in profiles) {
      if (isEnabled) {
        parametersList.add(profileName)
      }
      else {
        parametersList.add("-$profileName")
      }
    }
    return parametersList.parametersString
  }

  init {
    val textCompletionContributor = JTextCompletionContributor.create {
      val profiles = getProfiles(project, workingDirectoryField)
        .sortedWith(NaturalComparator.INSTANCE)
      profiles.map { TextCompletionInfo(it) } +
        profiles.map { TextCompletionInfo("-$it") }
    }
    TextCompletionPopup(project, this, textCompletionContributor)
  }

  private fun getProfiles(project: Project, workingDirectoryField: WorkingDirectoryField): Collection<String> {
    return getGlobalProfiles(project) + getLocalProfiles(project, workingDirectoryField)
  }

  private fun getGlobalProfiles(project: Project): Collection<String> {
    val projectsManager = MavenProjectsManager.getInstance(project)
    val explicitProfiles = projectsManager.explicitProfiles
    return explicitProfiles.enabledProfiles + explicitProfiles.disabledProfiles
  }

  private fun getLocalProfiles(project: Project, workingDirectoryField: WorkingDirectoryField): Collection<String> {
    val projectsManager = MavenProjectsManager.getInstance(project)
    val localFileSystem = LocalFileSystem.getInstance()
    val projectPath = workingDirectoryField.workingDirectory
    val projectDir = localFileSystem.refreshAndFindFileByPath(projectPath) ?: return emptyList()
    val mavenProject = projectsManager.findContainingProject(projectDir) ?: return emptyList()
    return mavenProject.profilesIds
  }
}