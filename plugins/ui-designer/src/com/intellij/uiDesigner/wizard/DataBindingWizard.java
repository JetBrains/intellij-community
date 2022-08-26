// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myBeanStep.myTfShortClassName;
  }

  @Override
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

  @Override
  protected String getHelpID() {
    return "guiDesigner.formCode.dataBind";
  }
}
