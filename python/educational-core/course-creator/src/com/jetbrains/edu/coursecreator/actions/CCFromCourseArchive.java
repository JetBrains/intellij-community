package com.jetbrains.edu.coursecreator.actions;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.templates.github.ZipUtil;
import com.jetbrains.edu.EduDocumentListener;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.*;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.oldCourseFormat.OldCourse;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Map;

public class CCFromCourseArchive extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CCFromCourseArchive.class.getName());

  public CCFromCourseArchive() {
    super("Unpack Course Archive", "Unpack Course Archive", AllIcons.FileTypes.Archive);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    unpackCourseArchive(project);
  }

  private static void unpackCourseArchive(final Project project) {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, true, true, false);

    final VirtualFile virtualFile = FileChooser.chooseFile(descriptor, project, null);
    if (virtualFile == null) {
      return;
    }
    final String basePath = project.getBasePath();
    if (basePath == null) return;
    final CCProjectService service = CCProjectService.getInstance(project);
    Reader reader = null;
    try {
      ZipUtil.unzip(null, new File(basePath), new File(virtualFile.getPath()), null, null, true);
      reader = new InputStreamReader(new FileInputStream(new File(basePath, EduNames.COURSE_META_FILE)));
      Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
      Course course = gson.fromJson(reader, Course.class);
      if (course == null || course.getLessons().isEmpty() || StringUtil.isEmptyOrSpaces(course.getLessons().get(0).getName())) {
        try {
          reader.close();
        }
        catch (IOException e) {
          LOG.error(e.getMessage());
        }
        reader = new InputStreamReader(new FileInputStream(new File(basePath, EduNames.COURSE_META_FILE)));
        OldCourse oldCourse = gson.fromJson(reader, OldCourse.class);
        course = EduUtils.transformOldCourse(oldCourse);
      }

      service.setCourse(course);
      project.getBaseDir().refresh(false, true);
      int index = 1;
      int taskIndex = 1;
      for (Lesson lesson : course.getLessons()) {
        final VirtualFile lessonDir = project.getBaseDir().findChild(EduNames.LESSON + String.valueOf(index));
        lesson.setIndex(index);
        if (lessonDir == null) continue;
        for (Task task : lesson.getTaskList()) {
          final VirtualFile taskDir = lessonDir.findChild(EduNames.TASK + String.valueOf(taskIndex));
          task.setIndex(taskIndex);
          task.setLesson(lesson);
          if (taskDir == null) continue;
          for (final Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                createAnswerFile(project, taskDir, taskDir, entry);
              }
            });
          }
          taskIndex += 1;
        }
        index += 1;
        taskIndex = 1;
      }
    }
    catch (FileNotFoundException e) {
      LOG.error(e.getMessage());
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    catch (JsonSyntaxException e) {
      LOG.error(e.getMessage());
    }
    finally {
      if (reader != null) {
        try {
          reader.close();
        }
        catch (IOException e) {
          LOG.error(e.getMessage());
        }
      }
    }
    synchronize(project);
  }

  public static void createAnswerFile(@NotNull final Project project,
                                      @NotNull final VirtualFile userFileDir,
                                      @NotNull final VirtualFile answerFileDir,
                                      @NotNull final Map.Entry<String, TaskFile> taskFileEntry) {
    final String name = taskFileEntry.getKey();
    final TaskFile taskFile = taskFileEntry.getValue();
    VirtualFile file = userFileDir.findChild(name);
    assert file != null;
    String answerFileName = file.getNameWithoutExtension() + ".answer." + file.getExtension();
    VirtualFile answerFile = answerFileDir.findChild(answerFileName);
    if (answerFile != null) {
      try {
        answerFile.delete(project);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    try {
      answerFile = userFileDir.createChildData(project, answerFileName);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    if (answerFile == null) return;

    final Document originDocument = FileDocumentManager.getInstance().getDocument(file);
    if (originDocument == null) {
      return;
    }
    final Document document = FileDocumentManager.getInstance().getDocument(answerFile);
    if (document == null) return;

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.replaceString(0, document.getTextLength(), originDocument.getCharsSequence());
          }
        });
      }
    }, "Create answer document", "Create answer document");
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
    final int offset = answerPlaceholder.getRealStartOffset(document);
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            final String text = document.getText(TextRange.create(offset, offset + answerPlaceholder.getLength()));
            answerPlaceholder.setTaskText(text);
            final VirtualFile hints = project.getBaseDir().findChild(EduNames.HINTS);
            if (hints != null) {
              final String hintFile = answerPlaceholder.getHint();
              final VirtualFile virtualFile = hints.findChild(hintFile);
              if (virtualFile != null) {
                final Document hintDocument = FileDocumentManager.getInstance().getDocument(virtualFile);
                if (hintDocument != null) {
                  final String hintText = hintDocument.getText();
                  answerPlaceholder.setHint(hintText);
                }
              }
            }

            document.replaceString(offset, offset + answerPlaceholder.getLength(), answerPlaceholder.getPossibleAnswer());
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

}