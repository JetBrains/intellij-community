package com.jetbrains.edu.learning.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.intellij.stepik.EduRemoteCourseTemplate;
import com.jetbrains.edu.learning.stepic.EduCourseUpdater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    final ArrayList<EduIntelliJProjectTemplate> templates = new ArrayList<>();
    final List<Course> courses = EduCourseUpdater.getInstance().getRemoteCourses();
    for (Course course : courses) {
      templates.add(new EduRemoteCourseTemplate(course));
    }
    Collections.addAll(templates, ApplicationManager.getApplication().getExtensions(EduIntelliJProjectTemplate.EP_NAME));
    return templates.toArray(new ProjectTemplate[templates.size()]);
  }

  @Override
  public Icon getGroupIcon(String group) {
    return AllIcons.Modules.Types.UserDefined;
  }
}
