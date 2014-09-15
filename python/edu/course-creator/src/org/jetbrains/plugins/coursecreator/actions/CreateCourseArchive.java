package org.jetbrains.plugins.coursecreator.actions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.CCProjectService;
import org.jetbrains.plugins.coursecreator.StudyDocumentListener;
import org.jetbrains.plugins.coursecreator.format.*;
import org.jetbrains.plugins.coursecreator.ui.CreateCourseArchiveDialog;

import java.io.*;
import java.util.*;
import java.util.zip.ZipOutputStream;

public class CreateCourseArchive extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CreateCourseArchive.class.getName());
  private String myZipName;
  private String myLocationDir;

  public void setZipName(String zipName) {
    myZipName = zipName;
  }

  public void setLocationDir(String locationDir) {
    myLocationDir = locationDir;
  }

  public CreateCourseArchive() {
    super("Generate course archive", "Generate course archive", AllIcons.FileTypes.Archive);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    if (course == null) return;
    CreateCourseArchiveDialog dlg = new CreateCourseArchiveDialog(project, this);
    dlg.show();
    if (dlg.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }
    final VirtualFile baseDir = project.getBaseDir();
    final Map<String, Lesson> lessons = course.getLessonsMap();
    //map to store initial task file
    final Map<TaskFile, TaskFile> taskFiles = new HashMap<TaskFile, TaskFile>();
    for (Map.Entry<String, Lesson> lesson : lessons.entrySet()) {
      final VirtualFile lessonDir = baseDir.findChild(lesson.getKey());
      if (lessonDir == null) continue;
      for (Map.Entry<String, Task> task : lesson.getValue().myTasksMap.entrySet()) {
        final VirtualFile taskDir = lessonDir.findChild(task.getKey());
        if (taskDir == null) continue;
        for (final Map.Entry<String, TaskFile> entry : task.getValue().task_files.entrySet()) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              createUserFile(project, taskFiles, taskDir, taskDir, entry);
            }
          });
        }
      }
    }
    generateJson(project);
    packCourse(baseDir, lessons);
    resetTaskFiles(taskFiles);
    synchronize(project);
  }

  public static void createUserFile(@NotNull final Project project,
                                    @NotNull final Map<TaskFile, TaskFile> taskFilesCopy,
                                    @NotNull final VirtualFile userFileDir,
                                    @NotNull final VirtualFile answerFileDir,
                                    @NotNull final Map.Entry<String, TaskFile> taskFiles) {
    final String name = taskFiles.getKey();
    VirtualFile file = userFileDir.findChild(name);
    if (file != null) {
      try {
        file.delete(project);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    try {
      userFileDir.createChildData(project, name);
    }
    catch (IOException e) {
      LOG.error(e);
    }

    file = userFileDir.findChild(name);
    assert file != null;
    String answerFileName = file.getNameWithoutExtension() + ".answer";
    VirtualFile answerFile = answerFileDir.findChild(answerFileName);
    if (answerFile == null) {
      return;
    }
    final Document answerDocument = FileDocumentManager.getInstance().getDocument(answerFile);
    if (answerDocument == null) {
      return;
    }
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return;
    final TaskFile taskFile = taskFiles.getValue();
    TaskFile taskFileSaved = new TaskFile();
    taskFile.copy(taskFileSaved);
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.replaceString(0, document.getTextLength(), answerDocument.getText());
          }
        });
      }
    }, "x", "qwe");
    InsertionListener listener = new InsertionListener(taskFile);
    document.addDocumentListener(listener);
    taskFilesCopy.put(taskFile, taskFileSaved);
    Collections.sort(taskFile.getTaskWindows());
    for (int i = taskFile.getTaskWindows().size() - 1; i >= 0; i--) {
      final TaskWindow taskWindow = taskFile.getTaskWindows().get(i);
      replaceTaskWindow(project, document, taskWindow);
    }
    document.removeDocumentListener(listener);
  }

  private static void replaceTaskWindow(@NotNull final Project project,
                                        @NotNull final Document document,
                                        @NotNull final TaskWindow taskWindow) {
    final String taskText = taskWindow.getTaskText();
    final int lineStartOffset = document.getLineStartOffset(taskWindow.line);
    final int offset = lineStartOffset + taskWindow.start;
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.replaceString(offset, offset + taskWindow.getReplacementLength(), taskText);
            FileDocumentManager.getInstance().saveDocument(document);
          }
        });
      }
    }, "x", "qwe");
  }

  private static void synchronize(@NotNull final Project project) {
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
    ProjectView.getInstance(project).refresh();
  }

  public static void resetTaskFiles(@NotNull final Map<TaskFile, TaskFile> taskFiles) {
    for (Map.Entry<TaskFile, TaskFile> entry : taskFiles.entrySet()) {
      TaskFile realTaskFile = entry.getKey();
      TaskFile savedTaskFile = entry.getValue();
      realTaskFile.update(savedTaskFile);
    }
  }

  private void packCourse(@NotNull final VirtualFile baseDir, @NotNull final Map<String, Lesson> lessons) {
    try {
      File zipFile = new File(myLocationDir, myZipName + ".zip");
      ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));

      for (Map.Entry<String, Lesson> entry : lessons.entrySet()) {
        final VirtualFile lessonDir = baseDir.findChild(entry.getKey());
        if (lessonDir == null) continue;

        ZipUtil.addFileOrDirRecursively(zos, null, new File(lessonDir.getPath()), lessonDir.getName(), new FileFilter() {
          @Override
          public boolean accept(File pathname) {
            return !pathname.getName().contains(".answer");
          }
        }, null);
      }
      ZipUtil.addFileOrDirRecursively(zos, null, new File(baseDir.getPath(), "hints"), "hints", null, null);
      ZipUtil.addFileOrDirRecursively(zos, null, new File(baseDir.getPath(), "course.json"), "course.json", null, null);
      ZipUtil.addFileOrDirRecursively(zos, null, new File(baseDir.getPath(), "test_helper.py"), "test_helper.py", null, null);
      zos.close();
      Messages.showInfoMessage("Course archive was saved to " + zipFile.getPath(), "Course Archive Was Created Successfully");
    }
    catch (IOException e1) {
      LOG.error(e1);
    }
  }

  private static void generateJson(@NotNull final Project project) {
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
    final String json = gson.toJson(course);
    final File courseJson = new File(project.getBasePath(), "course.json");
    FileWriter writer = null;
    try {
      writer = new FileWriter(courseJson);
      writer.write(json);
    }
    catch (IOException e) {
      Messages.showErrorDialog(e.getMessage(), "Failed to Generate Json");
      LOG.info(e);
    }
    catch (Exception e) {
      Messages.showErrorDialog(e.getMessage(), "Failed to Generate Json");
      LOG.info(e);
    }
    finally {
      try {
        if (writer != null) {
          writer.close();
        }
      }
      catch (IOException e1) {
        //close silently
      }
    }
  }

  private static class InsertionListener extends StudyDocumentListener {

    public InsertionListener(TaskFile taskFile) {
      super(taskFile);
    }

    @Override
    protected void updateTaskWindowLength(CharSequence fragment, TaskWindow taskWindow, int change) {
      //we don't need to update task window length
    }
  }
}