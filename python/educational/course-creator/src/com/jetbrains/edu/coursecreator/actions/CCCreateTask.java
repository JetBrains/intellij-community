package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.StudyItem;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.coursecreator.CCLanguageManager;
import com.jetbrains.edu.coursecreator.CCUtils;
import icons.EducationalIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class CCCreateTask extends CCCreateStudyItemActionBase {
  private static final Logger LOG = Logger.getInstance(CCCreateTask.class.getName());
  public static final String TITLE = "Create New " + EduNames.TASK_TITLED;

  public CCCreateTask() {
    super(EduNames.TASK_TITLED, TITLE, EducationalIcons.Task);
  }


  private static void createFromTemplateAndOpen(@NotNull final PsiDirectory taskDirectory,
                                                @Nullable final FileTemplate template,
                                                @Nullable IdeView view) {
    if (template == null) {
      return;
    }
    try {
      final PsiElement file = FileTemplateUtil.createFromTemplate(template, template.getName(), null, taskDirectory);
      if (view != null) {
        EditorHelper.openInEditor(file, false);
        view.selectElement(file);
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
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
    return new Function<VirtualFile, StudyItem>() {
      @Override
      public StudyItem fun(VirtualFile file) {
        if (item instanceof Task) {
          return ((Task)item).getLesson().getTask(file.getName());
        }
        return null;
      }
    };
  }

  @Override
  @Nullable
  protected PsiDirectory createItemDir(@NotNull final Project project, @NotNull final StudyItem item,
                                     @Nullable final IdeView view, @NotNull final PsiDirectory parentDirectory,
                                     @NotNull final Course course) {
    final PsiDirectory[] taskDirectory = new PsiDirectory[1];
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        taskDirectory[0] = DirectoryUtil.createSubdirectories(EduNames.TASK + item.getIndex(), parentDirectory, "\\/");
        if (taskDirectory[0] != null) {
          CCLanguageManager manager = CCUtils.getStudyLanguageManager(course);
          if (manager == null) {
            return;
          }
          createFromTemplateAndOpen(taskDirectory[0], manager.getTestsTemplate(project), view);
          createFromTemplateAndOpen(taskDirectory[0], FileTemplateManager.getInstance(project).getInternalTemplate("task.html"), view);
          String defaultExtension = manager.getDefaultTaskFileExtension();
          if (defaultExtension != null) {
            FileTemplate taskFileTemplate = manager.getTaskFileTemplateForExtension(project, defaultExtension);
            createFromTemplateAndOpen(taskDirectory[0], taskFileTemplate, view);
            if (taskFileTemplate != null) {
              String taskFileName = FileUtil.getNameWithoutExtension(taskFileTemplate.getName());
              ((Task)item).addTaskFile(taskFileName + "." + defaultExtension, 1);
            }
          }
        }
      }
    });
    return taskDirectory[0];
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