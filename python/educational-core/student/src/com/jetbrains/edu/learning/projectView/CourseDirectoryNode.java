package com.jetbrains.edu.learning.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import com.jetbrains.edu.learning.courseFormat.Task;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class CourseDirectoryNode extends StudyDirectoryNode {
  @NotNull protected final Project myProject;
  protected final ViewSettings myViewSettings;
  private final Course myCourse;

  public CourseDirectoryNode(@NotNull Project project,
                             PsiDirectory value,
                             ViewSettings viewSettings,
                             @NotNull Course course) {
    super(project, value, viewSettings);
    myProject = project;
    myViewSettings = viewSettings;
    myCourse = course;
  }

  @Override
  protected void updateImpl(PresentationData data) {
    setPresentation(data, myCourse.getName(), InteractiveLearningIcons.Course);
  }

  @Nullable
  public AbstractTreeNode modifyChildNode(AbstractTreeNode childNode) {
    Object value = childNode.getValue();
    if (value instanceof PsiDirectory) {
      PsiDirectory directory = (PsiDirectory)value;
      if (EduNames.SANDBOX_DIR.equals(directory.getName())) {
        return new SandboxDirectoryNode(myProject, directory, myViewSettings);
      }
      Lesson lesson = myCourse.getLesson(directory.getName());
      return lesson != null ? createChildDirectoryNode(lesson, directory) : null;
    }
    return null;
  }

  @Override
  public PsiDirectoryNode createChildDirectoryNode(StudyItem item, PsiDirectory directory) {
    final List<Lesson> lessons = myCourse.getLessons();
    final Lesson lesson = (Lesson)item;
    if (lessons.size() == 1) {
      final List<Task> tasks = lesson.getTaskList();
      if (tasks.size() == 1) {
        return new TaskDirectoryNode(myProject, (PsiDirectory)directory.getChildren()[0], myViewSettings, tasks.get(0));
      }
    }
    return new LessonDirectoryNode(myProject, directory, myViewSettings, lesson);
  }
}
