/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.wizard;

import com.intellij.CommonBundle;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class DataBindingWizard extends AbstractWizard<StepAdapter> {
  private final WizardData myData;
  private final Project myProject;
  private final BeanStep myBeanStep;

  public DataBindingWizard(@NotNull final Project project, @NotNull final WizardData data) {
    super(UIDesignerBundle.message("title.data.binding.wizard"), project);
    myProject = project;
    myData = data;

    myBeanStep = new BeanStep(myData);
    addStep(myBeanStep);
    addStep(new BindCompositeStep(myData));

    init();

    if (!data.myBindToNewBean) {
      doNextAction();
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myBeanStep.myTfShortClassName; 
  }

  protected void doOKAction() {
    CommandProcessor.getInstance().executeCommand(
      myProject,
      () -> ApplicationManager.getApplication().runWriteAction(
        () -> {
          try {
            Generator.generateDataBindingMethods(myData);
            super.doOKAction();
          }
          catch (Generator.MyException exc) {
            Messages.showErrorDialog(
              getContentPane(),
              exc.getMessage(),
              CommonBundle.getErrorTitle()
            );
          }
        }
      ),
      "",
      null
    );
  }

  protected String getHelpID() {
    return "guiDesigner.formCode.dataBind";
  }
}
