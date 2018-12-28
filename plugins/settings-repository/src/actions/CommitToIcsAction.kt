/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository.actions

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import org.jetbrains.settingsRepository.CommitToIcsDialog
import org.jetbrains.settingsRepository.ProjectId
import org.jetbrains.settingsRepository.icsManager
import java.util.*

private class ProjectChangeCollectConsumer(private val project: Project) {
  private var projectChanges: MutableList<Change>? = null

  fun consume(change: Change) {
    if (isProjectConfigFile(change.virtualFile, project)) {
      if (projectChanges == null) {
        projectChanges = SmartList<Change>()
      }
      projectChanges!!.add(change)
    }
  }

  fun getResult() = if (projectChanges == null) listOf<Change>() else projectChanges!!

  fun hasResult() = projectChanges != null
}

private fun getProjectId(project: Project): String? {
  val projectId = ServiceManager.getService<ProjectId>(project, ProjectId::class.java)!!
  if (projectId.uid == null) {
    if (icsManager.settings.doNoAskMapProject ||
        MessageDialogBuilder.yesNo("Settings Server Project Mapping", "Project is not mapped on Settings Server. Would you like to map?").project(project).doNotAsk(object : DialogWrapper.DoNotAskOption.Adapter() {
      override fun isSelectedByDefault(): Boolean {
        return true
      }

      override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
        icsManager.settings.doNoAskMapProject = isSelected
      }
    }).show() == Messages.YES) {
      projectId.uid = UUID.randomUUID().toString()
    }
  }

  return projectId.uid
}

private fun showDialog(project: Project, collectConsumer: ProjectChangeCollectConsumer, projectId: String?) {
  if (!collectConsumer.hasResult()) {
    return
  }

  var effectiveProjectId = projectId
  if (effectiveProjectId == null) {
    effectiveProjectId = getProjectId(project)
    if (effectiveProjectId == null) {
      return
    }
  }

  CommitToIcsDialog(project, effectiveProjectId, collectConsumer.getResult()).show()
}

private fun collectProjectChanges(changes: Collection<Change>, collectConsumer: ProjectChangeCollectConsumer) {
  for (change in changes) {
    collectConsumer.consume(change)
  }
}

private fun isProjectConfigFile(file: VirtualFile?, project: Project): Boolean {
  if (file == null) {
    return false
  }
  return FileUtil.isAncestor(project.basePath!!, file.path, true)
}
