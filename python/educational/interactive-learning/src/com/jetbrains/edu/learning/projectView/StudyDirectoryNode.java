package com.jetbrains.edu.learning.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.jetbrains.edu.learning.StudyNames;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.course.*;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class StudyDirectoryNode extends PsiDirectoryNode {
  private final PsiDirectory myValue;
  private final Project myProject;

  public StudyDirectoryNode(@NotNull final Project project,
                            PsiDirectory value,
                            ViewSettings viewSettings) {
    super(project, value, viewSettings);
    myValue = value;
    myProject = project;
  }

  @Override
  protected void updateImpl(PresentationData data) {
    data.setIcon(InteractiveLearningIcons.Task);
    String valueName = myValue.getName();
    StudyTaskManager studyTaskManager = StudyTaskManager.getInstance(myProject);
    Course course = studyTaskManager.getCourse();
    if (course == null) {
      return;
    }
    if (valueName.equals(myProject.getName())) {
      data.clearText();
      data.addText(course.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.BLACK));
      data.addText(" (" + valueName + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      return;
    }
    if (valueName.contains(Task.TASK_DIR)) {
      TaskFile file = null;
      for (PsiElement child : myValue.getChildren()) {
        VirtualFile virtualFile = child.getContainingFile().getVirtualFile();
        file = studyTaskManager.getTaskFile(virtualFile);
        if (file != null) {
          break;
        }
      }
      if (file != null) {
        Task task = file.getTask();
        setStudyAttributes(task, data, task.getName());
      }
    }
    if (valueName.contains(StudyNames.LESSON_DIR)) {
      int lessonIndex = Integer.parseInt(valueName.substring(StudyNames.LESSON_DIR.length())) - 1;
      Lesson lesson = course.getLessons().get(lessonIndex);
      setStudyAttributes(lesson, data, lesson.getName());
    }

    if (valueName.contains(Course.SANDBOX_DIR)) {
      if (myValue.getParent() != null) {
        if (!myValue.getParent().getName().contains(Course.SANDBOX_DIR)) {
          data.setPresentableText(Course.SANDBOX_DIR);
          data.setIcon(InteractiveLearningIcons.Sandbox);
          return;
        }
      }
    }
    data.setPresentableText(valueName);
  }

  @Override
  public int getTypeSortWeight(boolean sortByType) {
    String name = myValue.getName();
    if (name.contains(StudyNames.LESSON_DIR) || name.contains(Task.TASK_DIR)) {
      String logicalName = name.contains(StudyNames.LESSON_DIR) ? StudyNames.LESSON_DIR : Task.TASK_DIR;
      return StudyUtils.getIndex(name, logicalName) + 1;
    }
    return name.contains(Course.SANDBOX_DIR) ? 0 : 3;
  }

  private static void setStudyAttributes(Stateful stateful, PresentationData data, String additionalName) {
    StudyStatus taskStatus = stateful.getStatus();
    switch (taskStatus) {
      case Unchecked: {
        updatePresentation(data, additionalName, JBColor.BLACK, stateful instanceof Lesson ? InteractiveLearningIcons.Lesson : InteractiveLearningIcons.Task);
        break;
      }
      case Solved: {
        updatePresentation(data, additionalName, new JBColor(new Color(0, 134, 0), new Color(98, 150, 85)),
                           stateful instanceof Lesson ? InteractiveLearningIcons.LessonCompl : InteractiveLearningIcons.TaskCompl);
        break;
      }
      case Failed: {
        updatePresentation(data, additionalName, JBColor.RED, stateful instanceof Lesson ? InteractiveLearningIcons.Lesson : InteractiveLearningIcons.TaskProbl);
      }
    }
  }

  private static void updatePresentation(PresentationData data, String additionalName, JBColor color, Icon icon) {
    data.clearText();
    data.addText(additionalName, new SimpleTextAttributes(Font.PLAIN, color));
    data.setIcon(icon);
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Override
  public boolean canNavigateToSource() {
    return true;
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (myValue.getName().contains(Task.TASK_DIR)) {
      TaskFile taskFile = null;
      VirtualFile virtualFile =  null;
      for (PsiElement child : myValue.getChildren()) {
        VirtualFile childFile = child.getContainingFile().getVirtualFile();
        taskFile = StudyTaskManager.getInstance(myProject).getTaskFile(childFile);
        if (taskFile != null) {
          virtualFile = childFile;
          break;
        }
      }
      if (taskFile != null) {
        VirtualFile taskDir = virtualFile.getParent();
        Task task = taskFile.getTask();
        for (VirtualFile openFile : FileEditorManager.getInstance(myProject).getOpenFiles()) {
          FileEditorManager.getInstance(myProject).closeFile(openFile);
        }
        VirtualFile child = null;
        Map<String, TaskFile> taskFiles = task.getTaskFiles();
        for (Map.Entry<String, TaskFile> entry: taskFiles.entrySet()) {
          VirtualFile file = taskDir.findChild(entry.getKey());
          if (file != null) {
            FileEditorManager.getInstance(myProject).openFile(file, true);
          }
          if (!entry.getValue().getAnswerPlaceholders().isEmpty()) {
            child = file;
          }
        }
        if (child != null) {
          ProjectView.getInstance(myProject).select(child, child, false);
          FileEditorManager.getInstance(myProject).openFile(child, true);
        } else {
          VirtualFile[] children = taskDir.getChildren();
          if (children.length > 0) {
            ProjectView.getInstance(myProject).select(children[0], children[0], false);
          }
        }
      }
    }
  }

  @Override
  public boolean expandOnDoubleClick() {
    if (myValue.getName().contains(Task.TASK_DIR)) {
      return false;
    }
    return super.expandOnDoubleClick();
  }

  @Override
  protected boolean hasProblemFileBeneath() {
    return false;
  }
}
