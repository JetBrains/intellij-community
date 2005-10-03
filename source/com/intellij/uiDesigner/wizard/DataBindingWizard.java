package com.intellij.uiDesigner.wizard;

import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.CommonBundle;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class DataBindingWizard extends AbstractWizard{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.wizard.DataBindingWizard");
  private final WizardData myData;
  private final Project myProject;
  private final BeanStep myBeanStep;

  public DataBindingWizard(final Project project, final VirtualFile formFile, final WizardData data) {
    super(UIDesignerBundle.message("title.data.binding.wizard"), project);
    myProject = project;

    LOG.assertTrue(project != null);
    LOG.assertTrue(formFile != null);
    LOG.assertTrue(data != null);

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

  protected void updateStep() {
    super.updateStep();
    // "Finish" button is enabled only at the last step
    getFinishButton().setEnabled(getCurrentStep() == mySteps.size() - 1);
  }

  protected void doOKAction() {
    CommandProcessor.getInstance().executeCommand(
      myProject,
      new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              public void run() {
                try {
                  Generator.generateDataBindingMethods(myData);
                  DataBindingWizard.super.doOKAction();
                }
                catch (Generator.MyException exc) {
                  Messages.showErrorDialog(
                    getContentPane(),
                    exc.getMessage(),
                    CommonBundle.getErrorTitle()
                  );
                }
              }
            }
          );
        }
      },
      "",
      null
    );
  }

  protected String getHelpID() {
    return "guiDesigner.formCode.dataBind";
  }
}
