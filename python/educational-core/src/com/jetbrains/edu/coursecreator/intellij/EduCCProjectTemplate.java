package com.jetbrains.edu.coursecreator.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.AbstractModuleBuilder;
import com.intellij.openapi.ui.ValidationInfo;
import com.jetbrains.edu.learning.intellij.EduIntelliJProjectTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class EduCCProjectTemplate implements EduIntelliJProjectTemplate {
  @NotNull
  @Override
  public String getName() {
    return "Create a new course";
  }

  @Nullable
  @Override
  public String getDescription() {
    return "Assists in course creation";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Modules.Types.UserDefined;
  }

  @NotNull
  @Override
  public AbstractModuleBuilder createModuleBuilder() {
    return new EduCCModuleBuilder();
  }

  @Nullable
  @Override
  public ValidationInfo validateSettings() {
    return null;
  }
}
