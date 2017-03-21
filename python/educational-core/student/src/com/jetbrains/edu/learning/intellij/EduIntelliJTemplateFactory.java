package com.jetbrains.edu.learning.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@SuppressWarnings("unused") //used in other educational plugins that are stored in separate repository
public class EduIntelliJTemplateFactory extends ProjectTemplatesFactory {
  private static final String GROUP_NAME = "Education";

  @NotNull
  @Override
  public String[] getGroups() {
    return new String[]{GROUP_NAME};
  }

  @NotNull
  @Override
  public ProjectTemplate[] createTemplates(@Nullable String group, WizardContext context) {
    return ApplicationManager.getApplication().getExtensions(EduIntelliJProjectTemplate.EP_NAME);
  }

  @Override
  public Icon getGroupIcon(String group) {
    return AllIcons.Modules.Types.UserDefined;
  }
}
