package com.jetbrains.edu.learning.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.learning.StudyTaskManager;
import org.jetbrains.annotations.NotNull;

public class StudyToolWindowFactory implements ToolWindowFactory, DumbAware {
  public static final String STUDY_TOOL_WINDOW = "Task Description";


  @Override
  public void createToolWindowContent(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
    StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    final Course course = taskManager.getCourse();
    if (course != null) {
      final StudyToolWindow studyToolWindow = new StudyToolWindow(project);
      final ContentManager contentManager = toolWindow.getContentManager();
      final Content content = contentManager.getFactory().createContent(studyToolWindow, null, false);
      contentManager.addContent(content);
      Disposer.register(project, studyToolWindow);

    }
  }

}
