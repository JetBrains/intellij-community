/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.rest.sphinx;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.jetbrains.rest.RestPythonUtil;

/**
 * user : catherine
 */
public class RunSphinxQuickStartAction extends AnAction implements DumbAware {
  @Override
  public void update(final AnActionEvent event) {
    super.update(event);
    RestPythonUtil.updateSphinxQuickStartRequiredAction(event);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Presentation presentation = RestPythonUtil.updateSphinxQuickStartRequiredAction(e);
    assert presentation.isEnabled() &&  presentation.isVisible() : "Sphinx requirements for action are not satisfied";

    final Project project = e.getData(CommonDataKeys.PROJECT);

    if (project == null) return;

    Module module = e.getData(LangDataKeys.MODULE);
    if (module == null) {
      Module[] modules = ModuleManager.getInstance(project).getModules();
      module = modules.length == 0 ? null : modules [0];
    }

    if (module == null) return;
    final SphinxBaseCommand action = new SphinxBaseCommand();
    final Module finalModule = module;
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        action.execute(finalModule);
      }
    }, ModalityState.NON_MODAL);
  }
}
