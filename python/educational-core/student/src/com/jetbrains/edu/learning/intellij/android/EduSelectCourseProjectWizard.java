package com.jetbrains.edu.learning.intellij.android;

import com.intellij.ide.projectWizard.ProjectSettingsStep;
import com.intellij.ide.projectWizard.ProjectTemplateList;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.platform.ProjectTemplate;
import com.jetbrains.edu.learning.intellij.EduIntelliJProjectTemplate;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class EduSelectCourseProjectWizard extends AbstractProjectWizard {

  private StepSequence mySequence = new StepSequence();

  EduSelectCourseProjectWizard() {
    super("New Course", null, (String)null);
    init();
  }

  protected void init() {
    myWizardContext.setModulesProvider(ModulesProvider.EMPTY_MODULES_PROVIDER);
    MyChooseCourseStep step = new MyChooseCourseStep();
    mySequence.addCommonStep(step);
    mySequence.addCommonFinishingStep(new ProjectSettingsStep(myWizardContext), null);
    mySequence.getAllSteps().forEach(this::addStep);
    super.init();
  }

  @Override
  public StepSequence getSequence() {
    return mySequence;
  }

  private class MyChooseCourseStep extends ModuleWizardStep {
    private ProjectTemplateList myTemplateList = new ProjectTemplateList();

    @Override
    public JComponent getComponent() {
      return myTemplateList;
    }

    @Override
    public void updateDataModel() {
      myWizardContext.setProjectTemplate(myTemplateList.getSelectedTemplate());
    }

    @Override
    public void updateStep() {
      List<ProjectTemplate> templates = new ArrayList<>();
      Collections.addAll(templates, ApplicationManager.getApplication().getExtensions(EduIntelliJProjectTemplate.EP_NAME));
      myTemplateList.setTemplates(templates, false);
    }
  }
}
