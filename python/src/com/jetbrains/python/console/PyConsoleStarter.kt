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
package com.jetbrains.python.console

import com.intellij.execution.ExecutionManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Condition
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.util.Alarm

/**
 * Created by Yuli Fiterman on 9/12/2016.
 */
class PyConsoleStarter : StartupActivity {
  override fun runActivity(project: Project) {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      ConsoleStateTracker(project)
    }
  }

  private class ConsoleStateTracker(private val project: Project) {
    private val VISIBLE: String = "com.jetbrains.python.console.ToolWindowVisible"
    private var toolWindowVisible: Boolean = PropertiesComponent.getInstance(project).getBoolean(VISIBLE, false)
      set(value) {
        if (value != field) {
          PropertiesComponent.getInstance(project).setValue(VISIBLE, value, false)
        }
        field = value
      }

    private val toolWindow: ToolWindow?
      get() {
        return ToolWindowManager.getInstance(project).getToolWindow(PyConsoleToolWindowExecutor.TOOLWINDOW_ID)
      }


    init {
      ExecutionManager.getInstance(project).contentManager //Using this for the side effect. Force init
      toolWindow!!.isAutoHide = false
      toolWindow!!.isToHideOnEmptyContent = true
      toolWindow!!.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")


      val tvm = ToolWindowManager.getInstance(project) as ToolWindowManagerEx

      tvm.addToolWindowManagerListener(object : ToolWindowManagerListener {
        override fun toolWindowRegistered(id: String) {
        }

        override fun stateChanged() {
          val toolW = toolWindow ?: return;
          if (toolW.isVisible && toolW.contentManager.contentCount == 0) {
            val runner = PythonConsoleRunnerFactory.getInstance().createConsoleRunner(project, null);
            runner.runSync();
          }
          if (toolWindowVisible != toolW.isVisible) {
            ApplicationManager.getApplication().invokeLater(
                {
                  toolWindowVisible = toolWindow?.isVisible ?: false
                }, ModalityState.NON_MODAL, { !project.isOpen || project.isDisposed })


          }
        }
      })


      if (toolWindowVisible) {
        toolWindow!!.setAvailable(true) {
          TransactionGuard.submitTransaction(project, Runnable { toolWindow!!.show(null) })

        }
      }

    }
  }

}
