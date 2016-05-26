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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.io.ZipUtil;
import com.jetbrains.edu.coursecreator.CCLanguageManager;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.ui.CreateCourseArchiveDialog;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

public class CCCreateCourseArchive extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CCCreateCourseArchive.class.getName());
  private String myZipName;
  private String myLocationDir;

  public void setZipName(String zipName) {
    myZipName = zipName;
  }

  public void setLocationDir(String locationDir) {
    myLocationDir = locationDir;
  }

  public CCCreateCourseArchive() {
    super("Generate Course Archive", "Generate Course Archive", null);
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
    createCourseArchive(project, module);
  }

  private void createCourseArchive(final Project project, Module module) {
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) return;
    CreateCourseArchiveDialog dlg = new CreateCourseArchiveDialog(project, this);
    dlg.show();
    if (dlg.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }
    final VirtualFile baseDir = project.getBaseDir();

    VirtualFile archiveFolder = CCUtils.generateFolder(project, module, this, myZipName);
    if (archiveFolder == null) {
      return;
    }

    CCLanguageManager manager = CCUtils.getStudyLanguageManager(course);
    if (manager == null) {
      return;
    }
    FileFilter filter = pathname -> !manager.doNotPackFile(pathname);

    for (VirtualFile child : baseDir.getChildren()) {
      String name = child.getName();
      File fromFile = new File(child.getPath());
      if (CCUtils.GENERATED_FILES_FOLDER.equals(name) || ".idea".equals(name)
          || name.contains("iml") || manager.doNotPackFile(fromFile)) {
        continue;
      }
      copyChild(archiveFolder, filter, child, fromFile);
    }

    final List<Lesson> lessons = course.getLessons();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final Map<TaskFile, TaskFile> savedTaskFiles = new HashMap<TaskFile, TaskFile>();
        replaceAnswerFilesWithTaskFiles(savedTaskFiles);
        generateJson(project, archiveFolder);
        resetTaskFiles(savedTaskFiles);
        VirtualFileManager.getInstance().refreshWithoutFileWatcher(false);
        packCourse(archiveFolder);
        synchronize(project);
      }

      private void replaceAnswerFilesWithTaskFiles(Map<TaskFile, TaskFile> savedTaskFiles) {
        for (Lesson lesson : lessons) {
          final VirtualFile lessonDir = baseDir.findChild(EduNames.LESSON + String.valueOf(lesson.getIndex()));
          if (lessonDir == null) continue;
          for (Task task : lesson.getTaskList()) {
            final VirtualFile taskDir = lessonDir.findChild(EduNames.TASK + String.valueOf(task.getIndex()));
            if (taskDir == null) continue;
            for (final Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
              TaskFile taskFileCopy = new TaskFile();
              TaskFile taskFile = entry.getValue();
              TaskFile.copy(taskFile, taskFileCopy);
              savedTaskFiles.put(taskFile, taskFileCopy);

              VirtualFile userFileDir = VfsUtil.findRelativeFile(archiveFolder, lessonDir.getName(), taskDir.getName());
              if (userFileDir == null) {
                continue;
              }
              String taskFileName = entry.getKey();
              EduUtils.createStudentFileFromAnswer(project, userFileDir, taskDir, taskFileName, taskFile);
            }
          }
        }
      }
    });


  }

  private static void copyChild(VirtualFile archiveFolder, FileFilter filter, VirtualFile child, File fromFile) {
    File toFile = new File(archiveFolder.getPath(), child.getName());

    try {
      if (child.isDirectory()) {
        FileUtil.copyDir(fromFile, toFile, filter);
      }
      else {
        if (filter.accept(fromFile)) {
          FileUtil.copy(fromFile, toFile);
        }
      }
    }
    catch (IOException e) {
      LOG.info("Failed to copy" + fromFile.getPath(), e);
    }
  }

  private static void resetTaskFiles(Map<TaskFile, TaskFile> savedTaskFiles) {
    for (Map.Entry<TaskFile, TaskFile> entry : savedTaskFiles.entrySet()) {
      entry.getKey().setAnswerPlaceholders(entry.getValue().getAnswerPlaceholders());
    }
  }

  private static void synchronize(@NotNull final Project project) {
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
    ProjectView.getInstance(project).refresh();
  }

  private void packCourse(@NotNull final VirtualFile baseDir) {
    try {
      final File zipFile = new File(myLocationDir, myZipName + ".zip");
      ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
      VirtualFile[] courseFiles = baseDir.getChildren();
      for (VirtualFile file : courseFiles) {
        ZipUtil.addFileOrDirRecursively(zos, null, new File(file.getPath()), file.getName(), null, null);
      }
      zos.close();
      Messages.showInfoMessage("Course archive was saved to " + zipFile.getPath(), "Course Archive Was Created Successfully");
    }
    catch (IOException e1) {
      LOG.error(e1);
    }
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private static void generateJson(@NotNull final Project project, VirtualFile parentDir) {
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
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