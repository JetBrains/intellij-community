package com.jetbrains.edu.coursecreator;

import com.google.common.collect.Collections2;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.Function;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.PyCharmTask;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import org.apache.commons.codec.binary.Base64;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class CCUtils {
  public static final String ANSWER_EXTENSION_DOTTED = ".answer.";
  public static final String TASK_DESCRIPTION_TEXT = "Write task description here using markdown or html";
  private static final Logger LOG = Logger.getInstance(CCUtils.class);
  public static final String GENERATED_FILES_FOLDER = ".coursecreator";
  public static final String COURSE_MODE = "Course Creator";

  public static int getSubtaskIndex(@NotNull Project project, @NotNull VirtualFile file) {
    String fileName = file.getName();
    String name = FileUtil.getNameWithoutExtension(fileName);
    if (!isTestsFile(project, file)) {
      return -1;
    }
    if (!name.contains(EduNames.SUBTASK_MARKER)) {
      return 0;
    }
    int markerIndex = name.indexOf(EduNames.SUBTASK_MARKER);
    String index = name.substring(markerIndex + EduNames.SUBTASK_MARKER.length());
    if (index.isEmpty()) {
      return -1;
    }
    try {
      return Integer.valueOf(index);
    }
    catch (NumberFormatException e) {
      return -1;
    }
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
      (Collections2.filter(Arrays.asList(dirs), dir -> {
        final StudyItem item = getStudyItem.fun(dir);
        if (item == null) {
          return false;
        }
        int index = item.getIndex();
        return index > threshold;
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
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          generatedRoot.set(baseDir.createChildDirectory(this, GENERATED_FILES_FOLDER));
          VirtualFile contentRootForFile =
            ProjectRootManager.getInstance(module.getProject()).getFileIndex().getContentRootForFile(generatedRoot.get());
          if (contentRootForFile == null) {
            return;
          }
          ModuleRootModificationUtil.updateExcludedFolders(module, contentRootForFile, Collections.emptyList(),
                                                           Collections.singletonList(generatedRoot.get().getUrl()));
        }
        catch (IOException e) {
          LOG.info("Failed to create folder for generated files", e);
        }
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
    Language language = course.getLanguageById();
    if (language == null) {
      return false;
    }
    EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(language);
    if (configurator == null) {
      return false;
    }
    return configurator.isTestFile(file);
  }

  public static void updateActionGroup(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    presentation.setEnabledAndVisible(project != null && isCourseCreator(project));
  }

  /**
   * @param fromIndex -1 if task converted to TaskWithSubtasks, -2 if task converted from TaskWithSubtasks
   */
  public static void renameFiles(VirtualFile taskDir, Project project, int fromIndex) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      Map<VirtualFile, String> newNames = new HashMap<>();
      for (VirtualFile virtualFile : taskDir.getChildren()) {
        int subtaskIndex = getSubtaskIndex(project, virtualFile);
        if (subtaskIndex == -1) {
          continue;
        }
        if (subtaskIndex > fromIndex) {
          String index;
          if (fromIndex == -1) { // add new subtask
            index = "0";
          }
          else { // remove subtask
            index = fromIndex == -2 ? "" : Integer.toString(subtaskIndex - 1);
          }
          String fileName = virtualFile.getName();
          String nameWithoutExtension = FileUtil.getNameWithoutExtension(fileName);
          String extension = FileUtilRt.getExtension(fileName);
          int subtaskMarkerIndex = nameWithoutExtension.indexOf(EduNames.SUBTASK_MARKER);
          String newName = subtaskMarkerIndex == -1
                           ? nameWithoutExtension
                           : nameWithoutExtension.substring(0, subtaskMarkerIndex);
          newName += index.isEmpty() ? "" : EduNames.SUBTASK_MARKER;
          newName += index + "." + extension;
          newNames.put(virtualFile, newName);
        }
      }
      for (Map.Entry<VirtualFile, String> entry : newNames.entrySet()) {
        try {
          entry.getKey().rename(project, entry.getValue());
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    });
  }

  @Nullable
  public static Lesson createAdditionalLesson(Course course, Project project) {
    final VirtualFile baseDir = project.getBaseDir();
    EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(course.getLanguageById());

    final Lesson lesson = new Lesson();
    lesson.setName(EduNames.PYCHARM_ADDITIONAL);
    final Task task = new PyCharmTask();
    task.setLesson(lesson);
    task.setName(EduNames.PYCHARM_ADDITIONAL);
    task.setIndex(1);

    VfsUtilCore.visitChildrenRecursively(baseDir, new VirtualFileVisitor(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        final String name = file.getName();
        if (name.equals(EduNames.COURSE_META_FILE) || name.equals(EduNames.HINTS) || name.startsWith(".")) return false;
        String sanitizedName = FileUtil.sanitizeFileName(course.getName());
        final String archiveName = sanitizedName.startsWith("_") ? EduNames.COURSE : sanitizedName;
        if (name.equals(archiveName + ".zip")) return false;
        if (GENERATED_FILES_FOLDER.equals(name) || Project.DIRECTORY_STORE_FOLDER.equals(name)) {
          return false;
        }
        if (file.isDirectory()) return true;

        if (StudyUtils.isTestsFile(project, name)) return true;

        if (name.contains(".iml") || (configurator != null && configurator.excludeFromArchive(file.getPath()))) {
          return false;
        }
        final TaskFile taskFile = StudyUtils.getTaskFile(project, file);
        if (taskFile == null) {
          final String path = VfsUtilCore.getRelativePath(file, baseDir);
          try {
            if (EduUtils.isImage(file.getName())) {
              task.addTestsTexts(path, Base64.encodeBase64URLSafeString(FileUtil.loadBytes(file.getInputStream())));
            }
            else {
              task.addTestsTexts(path, FileUtil.loadTextAndClose(file.getInputStream()));
            }
          }
          catch (IOException e) {
            LOG.error("Can't find file " + path);
          }
        }
        return true;
      }
    });
    if (task.getTestsText().isEmpty()) return null;
    lesson.addTask(task);
    lesson.setIndex(course.getLessons().size());
    return lesson;
  }
}
