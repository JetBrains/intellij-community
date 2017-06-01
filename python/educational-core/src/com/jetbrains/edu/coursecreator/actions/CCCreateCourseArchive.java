package com.jetbrains.edu.coursecreator.actions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.io.ZipUtil;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.ui.CreateCourseArchiveDialog;
import com.jetbrains.edu.learning.StudySerializationUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

public class CCCreateCourseArchive extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CCCreateCourseArchive.class.getName());
  public static final String GENERATE_COURSE_ARCHIVE = "Generate Course Archive";
  private String myZipName;
  private String myLocationDir;

  public void setZipName(String zipName) {
    myZipName = zipName;
  }

  public void setLocationDir(String locationDir) {
    myLocationDir = locationDir;
  }

  public CCCreateCourseArchive() {
    super(GENERATE_COURSE_ARCHIVE, GENERATE_COURSE_ARCHIVE, null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    presentation.setEnabledAndVisible(project != null && CCUtils.isCourseCreator(project));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Module module = e.getData(LangDataKeys.MODULE);
    if (project == null || module == null) {
      return;
    }
    CreateCourseArchiveDialog dlg = new CreateCourseArchiveDialog(project, this);
    dlg.show();
    if (dlg.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }
    createCourseArchive(project, module, myZipName, myLocationDir, true);
    EduUsagesCollector.createdCourseArchive();
  }

  public static void createCourseArchive(final Project project, Module module, String zipName, String locationDir, boolean showMessage) {
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) return;
    final VirtualFile baseDir = project.getBaseDir();
    VirtualFile archiveFolder = CCUtils.generateFolder(project, module, zipName);
    if (archiveFolder == null) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        archiveFolder.refresh(false, true);
        Course courseCopy = course.copy();
        replaceAnswerFilesWithTaskFiles(courseCopy);
        courseCopy.sortLessons();
        //createAdditionalFiles(courseCopy);
        generateJson(archiveFolder, courseCopy);
        VirtualFileManager.getInstance().refreshWithoutFileWatcher(false);
        packCourse(archiveFolder, locationDir, zipName, showMessage);
        synchronize(project);
      }

      private void createAdditionalFiles(Course course) {
        final Lesson lesson = CCUtils.createAdditionalLesson(course, project);
        if (lesson != null) {
          course.addLesson(lesson);
        }
      }

      private void replaceAnswerFilesWithTaskFiles(Course courseCopy) {
        for (Lesson lesson : courseCopy.getLessons()) {
          String lessonDirName = EduNames.LESSON + String.valueOf(lesson.getIndex());
          final VirtualFile lessonDir = baseDir.findChild(lessonDirName);
          if (lessonDir == null) continue;
          for (Task task : lesson.getTaskList()) {
            final VirtualFile taskDir = task.getTaskDir(project);
            if (taskDir == null) continue;
            convertToStudentTaskFiles(task, taskDir);
            addTestsToTask(task);
            addDescriptions(task);
          }
        }
      }

      private void convertToStudentTaskFiles(Task task, VirtualFile taskDir) {
        final HashMap<String, TaskFile> studentTaskFiles = new HashMap<>();
        for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
          VirtualFile answerFile = taskDir.findFileByRelativePath(entry.getKey());
          if (answerFile == null) {
            continue;
          }
          final TaskFile studentFile = EduUtils.createStudentFile(project, answerFile, task, 0);
          if (studentFile != null) {
            studentTaskFiles.put(entry.getKey(), studentFile);
          }
        }
        task.taskFiles = studentTaskFiles;
      }

      private void addDescriptions(@NotNull final Task task) {
        final List<VirtualFile> descriptions = getDescriptionFiles(task, project);
        for (VirtualFile file : descriptions) {
          try {
            task.addTaskText(file.getName(), VfsUtilCore.loadText(file));
          }
          catch (IOException e) {
            LOG.warn("Failed to load text " + file.getName());
          }
        }
      }

      private void addTestsToTask(Task task) {
        final List<VirtualFile> testFiles = getTestFiles(task, project);
        for (VirtualFile file : testFiles) {
          try {
            task.addTestsTexts(file.getName(), VfsUtilCore.loadText(file));
          }
          catch (IOException e) {
            LOG.warn("Failed to load text " + file.getName());
          }
        }
      }

      private List<VirtualFile> getTestFiles(@NotNull Task task, @NotNull Project project) {
        List<VirtualFile> testFiles = new ArrayList<>();
        VirtualFile taskDir = task.getTaskDir(project);
        if (taskDir == null) {
          return testFiles;
        }
        testFiles.addAll(Arrays.stream(taskDir.getChildren())
                           .filter(file -> StudyUtils.isTestsFile(project, file.getName()))
                           .collect(Collectors.toList()));
        return testFiles;
      }

      private List<VirtualFile> getDescriptionFiles(@NotNull Task task, @NotNull Project project) {
        List<VirtualFile> testFiles = new ArrayList<>();
        VirtualFile taskDir = task.getTaskDir(project);
        if (taskDir == null) {
          return testFiles;
        }
        testFiles.addAll(Arrays.stream(taskDir.getChildren())
                           .filter(file -> StudyUtils.isTaskDescriptionFile(file.getName()))
                           .collect(Collectors.toList()));
        return testFiles;
      }
    });
  }

  private static void synchronize(@NotNull final Project project) {
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
    ProjectView.getInstance(project).refresh();
  }

  private static void packCourse(@NotNull final VirtualFile baseDir, String locationDir, String zipName, boolean showMessage) {
    try {
      final File zipFile = new File(locationDir, zipName + ".zip");
      ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
      VirtualFile[] courseFiles = baseDir.getChildren();
      for (VirtualFile file : courseFiles) {
        ZipUtil.addFileOrDirRecursively(zos, null, new File(file.getPath()), file.getName(), null, null);
      }
      zos.close();
      if (showMessage) {
        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showInfoMessage("Course archive was saved to " + zipFile.getPath(),
                                         "Course Archive Was Created Successfully"));

      }
    }
    catch (IOException e1) {
      LOG.error(e1);
    }
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private static void generateJson(VirtualFile parentDir, Course course) {
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().
      registerTypeAdapter(Task.class, new StudySerializationUtils.Json.TaskAdapter()).create();
    final String json = gson.toJson(course);
    final File courseJson = new File(parentDir.getPath(), EduNames.COURSE_META_FILE);
    OutputStreamWriter outputStreamWriter = null;
    try {
      outputStreamWriter = new OutputStreamWriter(new FileOutputStream(courseJson), "UTF-8");
      outputStreamWriter.write(json);
    }
    catch (Exception e) {
      Messages.showErrorDialog(e.getMessage(), "Failed to Generate Json");
      LOG.info(e);
    }
    finally {
      try {
        if (outputStreamWriter != null) {
          outputStreamWriter.close();
        }
      }
      catch (IOException e1) {
        //close silently
      }
    }
  }
}