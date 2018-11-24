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
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.*;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class StudyDirectoryNode extends PsiDirectoryNode {
  public static final JBColor LIGHT_GREEN = new JBColor(new Color(0, 134, 0), new Color(98, 150, 85));
  protected final PsiDirectory myValue;
  protected final Project myProject;

  public StudyDirectoryNode(@NotNull final Project project,
                            PsiDirectory value,
                            ViewSettings viewSettings) {
    super(project, value, viewSettings);
    myValue = value;
    myProject = project;
  }

  @Override
  protected void updateImpl(PresentationData data) {
    String valueName = myValue.getName();
    StudyTaskManager studyTaskManager = StudyTaskManager.getInstance(myProject);
    Course course = studyTaskManager.getCourse();
    if (course == null) {
      return;
    }
    if (valueName.equals(myProject.getBaseDir().getName())) {
      data.clearText();
      data.setIcon(InteractiveLearningIcons.Course);
      data.addText(course.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.BLACK));
    }
    else if (valueName.contains(EduNames.TASK)) {
      VirtualFile taskVirtualFile = myValue.getVirtualFile();
      VirtualFile lessonVirtualFile = taskVirtualFile.getParent();
      if (lessonVirtualFile != null) {
        Lesson lesson = course.getLesson(lessonVirtualFile.getName());
        if (lesson != null) {
          Task task = lesson.getTask(taskVirtualFile.getName());
          if (task != null) {
            setStudyAttributes(task, data, task.getName());
          }
        }
      }
    }
    else if (valueName.contains(EduNames.LESSON)) {
      int lessonIndex = Integer.parseInt(valueName.substring(EduNames.LESSON.length())) - 1;
      Lesson lesson = course.getLessons().get(lessonIndex);
      setStudyAttributes(lesson, data, lesson.getName());
      data.setPresentableText(valueName);
    }
    else if (valueName.contains(EduNames.SANDBOX_DIR)) {
      if (myValue.getParent() != null) {
        final String parentName = myValue.getParent().getName();
        if (!parentName.contains(EduNames.SANDBOX_DIR)) {
          data.setPresentableText(EduNames.SANDBOX_DIR);
          data.setIcon(InteractiveLearningIcons.Sandbox);
        }
      }
    }
    data.setPresentableText(valueName);
  }

  @Override
  public int getTypeSortWeight(boolean sortByType) {
    String name = myValue.getName();
    if (name.contains(EduNames.LESSON) || name.contains(EduNames.TASK)) {
      String logicalName = name.contains(EduNames.LESSON) ? EduNames.LESSON : EduNames.TASK;
      return EduUtils.getIndex(name, logicalName) + 1;
    }
    return name.contains(EduNames.SANDBOX_DIR) ? 0 : 3;
  }

  private static void setStudyAttributes(Lesson lesson, PresentationData data, String additionalName) {
    switch (lesson.getStatus()) {
      case Unchecked: {
        updatePresentation(data, additionalName, JBColor.BLACK, InteractiveLearningIcons.Lesson);
        break;
      }
      case Solved: {
        updatePresentation(data, additionalName, LIGHT_GREEN, InteractiveLearningIcons.LessonCompl);
        break;
      }
      case Failed: {
        updatePresentation(data, additionalName, JBColor.RED, InteractiveLearningIcons.Lesson);
      }
    }
  }

  protected void setStudyAttributes(Task task, PresentationData data, String name) {
    StudyStatus taskStatus = task.getStatus();
    switch (taskStatus) {
      case Unchecked: {
        updatePresentation(data, name, JBColor.BLACK, InteractiveLearningIcons.Task);
        break;
      }
      case Solved: {
        updatePresentation(data, name, LIGHT_GREEN, InteractiveLearningIcons.TaskCompl);
        break;
      }
      case Failed: {
        updatePresentation(data, name, JBColor.RED, InteractiveLearningIcons.TaskProbl);
      }
    }
  }

  protected static void updatePresentation(PresentationData data, String name, JBColor color, Icon icon) {
    data.clearText();
    data.addText(name, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color));
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
    final String myValueName = myValue.getName();
    if (myValueName.contains(EduNames.TASK)) {
      TaskFile taskFile = null;
      VirtualFile virtualFile =  null;
      for (PsiElement child : myValue.getChildren()) {
        VirtualFile childFile = child.getContainingFile().getVirtualFile();
        taskFile = StudyUtils.getTaskFile(myProject, childFile);
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
        for (Map.Entry<String, TaskFile> entry: task.getTaskFiles().entrySet()) {
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
    final String myValueName = myValue.getName();
    if (myValueName.contains(EduNames.TASK)) {
      return false;
    }
    return super.expandOnDoubleClick();
  }

  @Override
  protected boolean hasProblemFileBeneath() {
    return false;
  }

  @Override
  public String getNavigateActionText(boolean focusEditor) {
    return null;
  }
}
