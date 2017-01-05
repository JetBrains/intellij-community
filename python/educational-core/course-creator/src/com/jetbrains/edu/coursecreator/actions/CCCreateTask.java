package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.Function;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import com.jetbrains.edu.learning.courseFormat.Task;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class CCCreateTask extends CCCreateStudyItemActionBase {
  public static final String TITLE = "Create New " + EduNames.TASK_TITLED;

  public CCCreateTask() {
    super(EduNames.TASK_TITLED, TITLE, InteractiveLearningIcons.Task);
  }




  @Nullable
  @Override
  protected PsiDirectory getParentDir(@NotNull Project project, @NotNull Course course, @NotNull PsiDirectory directory) {
    if (isAddedAsLast(directory, project, course)) {
      return directory;
    }
    return directory.getParent();
  }

  @Override
  protected void addItem(@NotNull Course course, @NotNull StudyItem item) {
    if (item instanceof Task) {
      Task task = (Task)item;
      task.getLesson().addTask(task);
    }
  }

  @Override
  protected Function<VirtualFile, ? extends StudyItem> getStudyOrderable(@NotNull final StudyItem item) {
    return (Function<VirtualFile, StudyItem>)file -> {
      if (item instanceof Task) {
        return ((Task)item).getLesson().getTask(file.getName());
      }
      return null;
    };
  }

  @Override
  @Nullable
  protected PsiDirectory createItemDir(@NotNull final Project project, @NotNull final StudyItem item,
                                     @Nullable final IdeView view, @NotNull final PsiDirectory parentDirectory,
                                     @NotNull final Course course) {

    final Ref<PsiDirectory> taskDirectory = new Ref<>();
    ApplicationManager.getApplication().runWriteAction(() -> {
      String taskDirName = EduNames.TASK + item.getIndex();
      taskDirectory.set(DirectoryUtil.createSubdirectories(taskDirName, parentDirectory, "\\/"));
      if (taskDirectory.get() != null) {
        CCUtils.createTaskContent(project, view, course, taskDirectory.get());
      }
    });
    return taskDirectory.get();
  }

  @Override
  protected int getSiblingsSize(@NotNull Course course, @Nullable StudyItem parentItem) {
    if (parentItem instanceof Lesson) {
      return ((Lesson)parentItem).getTaskList().size();
    }
    return 0;
  }

  @Nullable
  @Override
  protected StudyItem getParentItem(@NotNull Course course, @NotNull PsiDirectory directory) {
    Task task = (Task)getThresholdItem(course, directory);
    if (task == null) {
      return course.getLesson(directory.getName());
    }
    return task.getLesson();
  }

  @Nullable
  @Override
  protected StudyItem getThresholdItem(@NotNull Course course, @NotNull PsiDirectory sourceDirectory) {
    return EduUtils.getTask(sourceDirectory, course);
  }

  @Override
  protected boolean isAddedAsLast(@NotNull PsiDirectory sourceDirectory,
                                  @NotNull Project project,
                                  @NotNull Course course) {
    return course.getLesson(sourceDirectory.getName()) != null;
  }

  @Override
  protected List<? extends StudyItem> getSiblings(@NotNull Course course, @Nullable StudyItem parentItem) {
    if (parentItem instanceof Lesson) {
      return ((Lesson)parentItem).getTaskList();
    }
    return Collections.emptyList();
  }

  @Override
  protected String getItemName() {
    return EduNames.TASK;
  }

  @Override
  protected StudyItem createAndInitItem(@NotNull Course course, @Nullable StudyItem parentItem, String name, int index) {
    final Task task = new Task(name);
    task.setIndex(index);
    if (parentItem == null) {
      return null;
    }
    task.setLesson(((Lesson)parentItem));
    return task;
  }
}