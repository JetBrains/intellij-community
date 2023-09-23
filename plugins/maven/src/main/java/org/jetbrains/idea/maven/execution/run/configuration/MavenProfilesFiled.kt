// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.run.configuration

import com.intellij.execution.configurations.ParametersList
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionField
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfoRenderer
import com.intellij.openapi.externalSystem.service.ui.completion.collector.TextCompletionCollector
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.idea.maven.project.MavenProjectsManager

class MavenProfilesFiled(
  private val project: Project,
  private val workingDirectoryField: WorkingDirectoryField,
  parentDisposable: Disposable
) : TextCompletionField<TextCompletionInfo>(project) {

  private val textProperty = AtomicProperty("")

  var profiles by textProperty.transform(::decodeProfiles, ::encodeProfiles)

  override val completionCollector = TextCompletionCollector.async(parentDisposable) {
    val profiles = getProfiles(project, workingDirectoryField)
      .sortedWith(NaturalComparator.INSTANCE)
    val enableProfileCompletion = profiles.map { TextCompletionInfo(it) }
    val disableProfileCompletion = profiles.map { TextCompletionInfo("-$it") }
    return@async enableProfileCompletion + disableProfileCompletion
  }

  init {
    renderer = TextCompletionInfoRenderer()
    completionType = CompletionType.REPLACE_WORD
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

  private suspend fun getProfiles(project: Project, workingDirectoryField: WorkingDirectoryField): Collection<String> {
    return getGlobalProfiles(project) + getLocalProfiles(project, workingDirectoryField)
  }

  private suspend fun getGlobalProfiles(project: Project): Collection<String> {
    return blockingContext {
      val projectsManager = MavenProjectsManager.getInstance(project)
      projectsManager.availableProfiles
    }
  }

  private suspend fun getLocalProfiles(project: Project, workingDirectoryField: WorkingDirectoryField): Collection<String> {
    val projectDirectory = blockingContext { workingDirectoryField.getWorkingDirectoryVirtualFile() }
                           ?: return emptyList()
    val projectsManager = MavenProjectsManager.getInstance(project)
    val mavenProject = readAction { projectsManager.findContainingProject(projectDirectory) }
                       ?: return emptyList()
    return mavenProject.profilesIds
  }
}