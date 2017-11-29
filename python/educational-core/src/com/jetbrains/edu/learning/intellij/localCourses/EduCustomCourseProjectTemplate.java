package com.jetbrains.edu.learning.intellij.localCourses;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.AbstractModuleBuilder;
import com.intellij.openapi.ui.ValidationInfo;
import com.jetbrains.edu.learning.intellij.EduIntelliJProjectTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class EduCustomCourseProjectTemplate implements EduIntelliJProjectTemplate {
  @NotNull
  @Override
  public String getName() {
    return "Load an existing course";
  }

  @Nullable
  @Override
  public String getDescription() {
    return "Select and add local course from *.zip";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.General.SecondaryGroup;
  }

  @NotNull
  @Override
  public AbstractModuleBuilder createModuleBuilder() {
    return new EduCustomCourseModuleBuilder();
  }

  @Nullable
  @Override
  public ValidationInfo validateSettings() {
    return null;
  }
}
