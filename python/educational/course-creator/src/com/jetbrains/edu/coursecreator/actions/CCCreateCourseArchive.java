package com.jetbrains.edu.coursecreator.actions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.io.ZipUtil;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.coursecreator.CCLanguageManager;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.ui.CreateCourseArchiveDialog;
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
    super("Generate Course Archive", "Generate Course Archive", AllIcons.FileTypes.Archive);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    CCProjectService.setCCActionAvailable(e);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    createCourseArchive(project);
  }

  private void createCourseArchive(final Project project) {
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    if (course == null) return;
    CreateCourseArchiveDialog dlg = new CreateCourseArchiveDialog(project, this);
    dlg.show();
    if (dlg.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }
    final VirtualFile baseDir = project.getBaseDir();
    final List<Lesson> lessons = course.getLessons();

    final Map<TaskFile, TaskFile> savedTaskFiles = new HashMap<TaskFile, TaskFile>();
    for (Lesson lesson : lessons) {
      final VirtualFile lessonDir = baseDir.findChild(EduNames.LESSON + String.valueOf(lesson.getIndex()));
      if (lessonDir == null) continue;
      for (Task task : lesson.getTaskList()) {
        final VirtualFile taskDir = lessonDir.findChild(EduNames.TASK + String.valueOf(task.getIndex()));
        if (taskDir == null) continue;
        for (final Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              TaskFile taskFileCopy = new TaskFile();
              TaskFile taskFile = entry.getValue();
              TaskFile.copy(taskFile, taskFileCopy);
              savedTaskFiles.put(taskFile, taskFileCopy);
              EduUtils.createStudentFileFromAnswer(project, taskDir, taskDir, entry.getKey(), taskFile);
            }
          });
        }
      }
    }
    generateJson(project);
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(false);
    packCourse(baseDir, course);
    synchronize(project);
    resetTaskFiles(savedTaskFiles);
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

  private void packCourse(@NotNull final VirtualFile baseDir, @NotNull final Course course) {
    try {
      final File zipFile = new File(myLocationDir, myZipName + ".zip");
      ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
      final CCLanguageManager manager = CCUtils.getStudyLanguageManager(course);
      VirtualFile[] courseFiles = baseDir.getChildren();
      for (VirtualFile file : courseFiles) {
        ZipUtil.addFileOrDirRecursively(zos, null, new File(file.getPath()), file.getName(), new FileFilter() {
          @Override
          public boolean accept(File pathname) {
            String name = pathname.getName();
            String nameWithoutExtension = FileUtil.getNameWithoutExtension(pathname);
            if (nameWithoutExtension.endsWith(".answer") || name.contains(EduNames.WINDOWS_POSTFIX) || name.contains(".idea")
              || FileUtil.filesEqual(pathname, zipFile)) {
              return false;
            }
            return manager != null && !manager.doNotPackFile(pathname);
          }
        }, null);
      }
      zos.close();
      Messages.showInfoMessage("Course archive was saved to " + zipFile.getPath(), "Course Archive Was Created Successfully");
    }
    catch (IOException e1) {
      LOG.error(e1);
    }
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private static void generateJson(@NotNull final Project project) {
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
    final String json = gson.toJson(course);
    final File courseJson = new File(project.getBasePath(), EduNames.COURSE_META_FILE);
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