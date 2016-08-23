package com.jetbrains.edu.coursecreator;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.intellij.ide.projectView.actions.MarkRootActionBase;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.Function;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class CCUtils {
  public static final String ANSWER_EXTENSION_DOTTED = ".answer.";
  private static final Logger LOG = Logger.getInstance(CCUtils.class);
  public static final String GENERATED_FILES_FOLDER = ".coursecreator";
  public static final String COURSE_MODE = "Course Creator";

  @Nullable
  public static CCLanguageManager getStudyLanguageManager(@NotNull final Course course) {
    Language language = Language.findLanguageByID(course.getLanguageID());
    return language == null ? null : CCLanguageManager.INSTANCE.forLanguage(language);
  }

  /**
   * This method decreases index and updates directory names of
   * all tasks/lessons that have higher index than specified object
   *
   * @param dirs         directories that are used to get tasks/lessons
   * @param getStudyItem function that is used to get task/lesson from VirtualFile. This function can return null
   * @param threshold    index is used as threshold
   * @param prefix       task or lesson directory name prefix
   */
  public static void updateHigherElements(VirtualFile[] dirs,
                                          @NotNull final Function<VirtualFile, ? extends StudyItem> getStudyItem,
                                          final int threshold,
                                          final String prefix,
                                          final int delta) {
    ArrayList<VirtualFile> dirsToRename = new ArrayList<>
      (Collections2.filter(Arrays.asList(dirs), new Predicate<VirtualFile>() {
        @Override
        public boolean apply(VirtualFile dir) {
          final StudyItem item = getStudyItem.fun(dir);
          if (item == null) {
            return false;
          }
          int index = item.getIndex();
          return index > threshold;
        }
      }));
    Collections.sort(dirsToRename, (o1, o2) -> {
      StudyItem item1 = getStudyItem.fun(o1);
      StudyItem item2 = getStudyItem.fun(o2);
      //if we delete some dir we should start increasing numbers in dir names from the end
      return (-delta) * EduUtils.INDEX_COMPARATOR.compare(item1, item2);
    });

    for (final VirtualFile dir : dirsToRename) {
      final StudyItem item = getStudyItem.fun(dir);
      final int newIndex = item.getIndex() + delta;
      item.setIndex(newIndex);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          try {
            dir.rename(this, prefix + newIndex);
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      });
    }
  }

  public static boolean isLessonDir(PsiDirectory sourceDirectory) {
    if (sourceDirectory == null) {
      return false;
    }
    Project project = sourceDirectory.getProject();
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course != null && isCourseCreator(project) && course.getLesson(sourceDirectory.getName()) != null) {
      return true;
    }
    return false;
  }


  public static VirtualFile getGeneratedFilesFolder(@NotNull Project project, @NotNull Module module) {
    VirtualFile baseDir = project.getBaseDir();
    VirtualFile folder = baseDir.findChild(GENERATED_FILES_FOLDER);
    if (folder != null) {
      return folder;
    }
    final Ref<VirtualFile> generatedRoot = new Ref<>();
    DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              generatedRoot.set(baseDir.createChildDirectory(this, GENERATED_FILES_FOLDER));
              final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
              ContentEntry entry = MarkRootActionBase.findContentEntry(model, generatedRoot.get());
              if (entry == null) {
                LOG.info("Failed to find contentEntry for archive folder");
                return;
              }
              entry.addExcludeFolder(generatedRoot.get());
              model.commit();
              module.getProject().save();
            }
            catch (IOException e) {
              LOG.info("Failed to create folder for generated files", e);
            }
          }
        });
      }
    });
    return generatedRoot.get();
  }

  @Nullable
  public static VirtualFile generateFolder(@NotNull Project project, @NotNull Module module, String name) {
    VirtualFile generatedRoot = getGeneratedFilesFolder(project, module);
    if (generatedRoot == null) {
      return null;
    }

    final Ref<VirtualFile> folder = new Ref<>(generatedRoot.findChild(name));
    //need to delete old folder
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        if (folder.get() != null) {
          folder.get().delete(null);
        }
        folder.set(generatedRoot.createChildDirectory(null, name));
      }
      catch (IOException e) {
        LOG.info("Failed to generate folder " + name, e);
      }
    });
    return folder.get();
  }

  public static boolean isCourseCreator(@NotNull Project project) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return false;
    }

    return COURSE_MODE.equals(course.getCourseMode());
  }

  public static boolean isTestsFile(@NotNull Project project, @NotNull VirtualFile file) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return false;
    }
    CCLanguageManager manager = getStudyLanguageManager(course);
    if (manager == null) {
      return false;
    }
    return manager.isTestFile(file);
  }

  public static void createResourceFile(VirtualFile createdFile, Course course, VirtualFile taskVF) {
    VirtualFile lessonVF = taskVF.getParent();
    if (lessonVF == null) {
      return;
    }

    String taskResourcesPath = FileUtil.join(course.getCourseDirectory(), lessonVF.getName(), taskVF.getName());
    File taskResourceFile = new File(taskResourcesPath);
    if (!taskResourceFile.exists()) {
      if (!taskResourceFile.mkdirs()) {
        LOG.info("Failed to create resources for task " + taskResourcesPath);
      }
    }
    try {
      File toFile = new File(taskResourceFile, createdFile.getName());
      FileUtil.copy(new File(createdFile.getPath()), toFile);
    }
    catch (IOException e) {
      LOG.info("Failed to copy created task file to resources " + createdFile.getPath());
    }
  }


  public static void updateResources(Project project, Task task, VirtualFile taskDir) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    VirtualFile lessonVF = taskDir.getParent();
    if (lessonVF == null) {
      return;
    }

    String taskResourcesPath = FileUtil.join(course.getCourseDirectory(), lessonVF.getName(), taskDir.getName());
    File taskResourceFile = new File(taskResourcesPath);
    if (!taskResourceFile.exists()) {
      if (!taskResourceFile.mkdirs()) {
        LOG.info("Failed to create resources for task " + taskResourcesPath);
      }
    }
    VirtualFile studentDir = LocalFileSystem.getInstance().findFileByIoFile(taskResourceFile);
    if (studentDir == null) {
      return;
    }
    for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
      String name = entry.getKey();
      VirtualFile answerFile = taskDir.findChild(name);
      if (answerFile == null) {
        continue;
      }
      ApplicationManager.getApplication().runWriteAction(() -> {
        EduUtils.createStudentFile(CCUtils.class, project, answerFile, studentDir, null);
      });
    }
  }

  public static void updateActionGroup(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    presentation.setEnabledAndVisible(project != null && isCourseCreator(project));
  }
}
