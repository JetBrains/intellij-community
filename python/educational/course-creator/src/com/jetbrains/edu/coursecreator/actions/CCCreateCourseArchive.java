package com.jetbrains.edu.coursecreator.actions;

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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.io.ZipUtil;
import com.jetbrains.edu.EduDocumentListener;
import com.jetbrains.edu.courseFormat.*;
import com.jetbrains.edu.coursecreator.CCLanguageManager;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.ui.CreateCourseArchiveDialog;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.AbstractMap;
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
    final Map<String, Lesson> lessons = service.getLessonsMap();

    for (Map.Entry<String, Task> task : service.getTasksMap().entrySet()) {
      final VirtualFile taskDir = LocalFileSystem.getInstance().findFileByPath(task.getKey());
      if (taskDir == null) continue;
      for (final Map.Entry<String, TaskFile> entry : task.getValue().getTaskFiles().entrySet()) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            TaskFile taskFileCopy = new TaskFile();
            TaskFile.copy(entry.getValue(), taskFileCopy);
            createUserFile(project, taskDir, taskDir, new AbstractMap.SimpleEntry<String, TaskFile>(entry.getKey(), taskFileCopy));
          }
        });
      }
    }
    generateJson(project);
    packCourse(baseDir, lessons, course);
    synchronize(project);
  }

  public static void createUserFile(@NotNull final Project project,
                                    @NotNull final VirtualFile userFileDir,
                                    @NotNull final VirtualFile answerFileDir,
                                    @NotNull final Map.Entry<String, TaskFile> taskFileEntry) {
    final String name = taskFileEntry.getKey();
    final TaskFile taskFile = taskFileEntry.getValue();
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
    String answerFileName = file.getNameWithoutExtension() + ".answer." + file.getExtension();
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

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.replaceString(0, document.getTextLength(), answerDocument.getCharsSequence());
          }
        });
      }
    }, "x", "qwe");
    EduDocumentListener listener = new EduDocumentListener(taskFile, false);
    document.addDocumentListener(listener);
    taskFile.sortAnswerPlaceholders();
    for (int i = taskFile.getAnswerPlaceholders().size() - 1; i >= 0; i--) {
      final AnswerPlaceholder answerPlaceholder = taskFile.getAnswerPlaceholders().get(i);
      replaceAnswerPlaceholder(project, document, answerPlaceholder);
    }
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            FileDocumentManager.getInstance().saveDocument(document);
          }
        });
      }
    }, "x", "qwe");
    document.removeDocumentListener(listener);
  }

  private static void replaceAnswerPlaceholder(@NotNull final Project project,
                                               @NotNull final Document document,
                                               @NotNull final AnswerPlaceholder answerPlaceholder) {
    final String taskText = answerPlaceholder.getTaskText();
    final int offset = answerPlaceholder.getRealStartOffset(document);
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.replaceString(offset, offset + answerPlaceholder.getPossibleAnswerLength(), taskText);
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

  private void packCourse(@NotNull final VirtualFile baseDir, @NotNull final Map<String, Lesson> lessons, @NotNull final Course course) {
    try {
      File zipFile = new File(myLocationDir, myZipName + ".zip");
      ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
      final CCLanguageManager manager = CCUtils.getStudyLanguageManager(course);
      for (Map.Entry<String, Lesson> entry : lessons.entrySet()) {
        final VirtualFile lessonDir = baseDir.findChild(entry.getKey());
        if (lessonDir == null) continue;
        ZipUtil.addFileOrDirRecursively(zos, null, new File(lessonDir.getPath()), lessonDir.getName(), new FileFilter() {
          @Override
          public boolean accept(File pathname) {
            String name = pathname.getName();
            String nameWithoutExtension = FileUtil.getNameWithoutExtension(pathname);
            if (nameWithoutExtension.endsWith(".answer") || name.contains("_windows")) {
              return false;
            }
            return manager == null || manager.packFile(pathname);
          }
        }, null);
      }
      packFile("hints", zos, baseDir);
      packFile("course.json", zos, baseDir);
      if (manager != null) {
        String[] additionalFilesToPack = manager.getAdditionalFilesToPack();
        for (String filename: additionalFilesToPack) {
          packFile(filename, zos, baseDir);
        }
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
    final File courseJson = new File(project.getBasePath(), "course.json");
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

  private static void packFile(@NotNull final String filename,
                               @NotNull final ZipOutputStream zipOutputStream,
                               @NotNull final VirtualFile baseDir) {
    try {
      File file = new File(baseDir.getPath(), filename);
      if (!file.exists()) {
        return;
      }
      ZipUtil.addFileOrDirRecursively(zipOutputStream, null, file, filename, null, null);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }
}