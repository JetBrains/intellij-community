package com.jetbrains.edu.learning.intellij.stepik;

import com.intellij.ide.util.projectWizard.AbstractModuleBuilder;
import com.intellij.openapi.ui.ValidationInfo;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.intellij.EduIntelliJProjectTemplate;
import icons.EducationalCoreIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class EduRemoteCourseTemplate implements EduIntelliJProjectTemplate {
  private final Course myCourse;

  public EduRemoteCourseTemplate(Course course) {
    myCourse = course;
  }

  @NotNull
  @Override
  public String getName() {
    return myCourse.getName();
  }

  @Nullable
  @Override
  public String getDescription() {
    return myCourse.getDescription();
  }

  @Override
  public Icon getIcon() {
    return EducationalCoreIcons.Stepik;
  }

  @NotNull
  @Override
  public AbstractModuleBuilder createModuleBuilder() {
    return new EduRemoteCourseModuleBuilder(myCourse);
  }

  @Nullable
  @Override
  public ValidationInfo validateSettings() {
    return null;
  }
}
