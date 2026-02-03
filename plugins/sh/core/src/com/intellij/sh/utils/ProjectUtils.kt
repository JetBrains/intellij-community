package com.intellij.sh.utils

import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.ProjectUtil.getActiveProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import javax.swing.JComponent

internal object ProjectUtil {
  @JvmStatic
  fun getProject(component: JComponent?): Project {
    return component?.let(ProjectUtil::getProjectForComponent) ?: getActiveProject() ?: ProjectManager.getInstance().defaultProject
  }
}