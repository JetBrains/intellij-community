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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.templates.github.ZipUtil;
import com.intellij.util.containers.HashMap;
import com.jetbrains.edu.EduDocumentListener;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.courseFormat.*;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.coursecreator.actions.oldCourseFormat.*;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Map;

public class CCFromCourseArchive extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CCFromCourseArchive.class.getName());

  public CCFromCourseArchive() {
    super("Unpack Course Archive", "Unpack Course Archive", AllIcons.FileTypes.Archive);
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
    Reader reader;
    try {
      ZipUtil.unzip(null, new File(basePath), new File(virtualFile.getPath()), null, null, true);
      reader = new InputStreamReader(new FileInputStream(new File(basePath, "course.json")));
      Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
      OldCourse oldCourse = gson.fromJson(reader, OldCourse.class);
      Course course = transformOldCourse(oldCourse);

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
    synchronize(project);
  }

  @NotNull
  private static Course transformOldCourse(@NotNull final OldCourse oldCourse) {
    Course course = new Course();
    course.setDescription(oldCourse.description);
    course.setName(oldCourse.name);
    course.setAuthors(new String[]{oldCourse.author});

    final ArrayList<Lesson> lessons = new ArrayList<Lesson>();
    for (OldLesson oldLesson : oldCourse.lessons) {
      final Lesson lesson = new Lesson();
      lesson.setName(oldLesson.name);
      lesson.setIndex(oldLesson.myIndex);

      final ArrayList<Task> tasks = new ArrayList<Task>();
      for (OldTask oldTask : oldLesson.taskList) {
        final Task task = new Task();
        task.setIndex(oldTask.myIndex);
        task.setName(oldTask.name);

        final HashMap<String, TaskFile> taskFiles = new HashMap<String, TaskFile>();
        for (Map.Entry<String, OldTaskFile> entry : oldTask.taskFiles.entrySet()) {
          final TaskFile taskFile = new TaskFile();
          final OldTaskFile oldTaskFile = entry.getValue();
          taskFile.setIndex(oldTaskFile.myIndex);

          final ArrayList<AnswerPlaceholder> placeholders = new ArrayList<AnswerPlaceholder>();
          for (OldTaskWindow window : oldTaskFile.taskWindows) {
            final AnswerPlaceholder placeholder = new AnswerPlaceholder();

            placeholder.setIndex(window.myIndex);
            placeholder.setHint(window.hint);
            placeholder.setLength(window.length);
            placeholder.setLine(window.line);
            placeholder.setPossibleAnswer(window.possibleAnswer);
            placeholder.setStart(window.start);

            placeholders.add(placeholder);
          }

          taskFile.setAnswerPlaceholders(placeholders);
          taskFiles.put(entry.getKey(), taskFile);
        }
        task.taskFiles = taskFiles;
        tasks.add(task);
      }

      lesson.taskList = tasks;

      lessons.add(lesson);
    }
    course.setLessons(lessons);
    return course;
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
            final VirtualFile hints = project.getBaseDir().findChild("hints");
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