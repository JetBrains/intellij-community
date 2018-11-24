package com.jetbrains.edu.learning.intellij.android;

import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.PlatformUtils;
import com.jetbrains.edu.learning.intellij.EduIntelliJProjectTemplate;

public class EduStartLearningAction extends AnAction {

  public EduStartLearningAction() {
    super(AllIcons.Modules.Types.UserDefined);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    EduSelectCourseProjectWizard wizard = new EduSelectCourseProjectWizard();
    NewProjectUtil.createNewProject(AnAction.getEventProject(e), wizard);
  }

  @Override
  public void update(AnActionEvent e) {
    if (ApplicationManager.getApplication().getExtensions(EduIntelliJProjectTemplate.EP_NAME).length < 1) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    if (!PlatformUtils.isJetBrainsProduct()) {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }
}
