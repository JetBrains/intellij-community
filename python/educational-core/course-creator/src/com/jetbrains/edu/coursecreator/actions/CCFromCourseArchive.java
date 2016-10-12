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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.templates.github.ZipUtil;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudySerializationUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.core.EduDocumentListener;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.*;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Map;

import static com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator.OUR_COURSES_DIR;

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
    Reader reader = null;
    try {
      ZipUtil.unzip(null, new File(basePath), new File(virtualFile.getPath()), null, null, true);
      File courseMetaFile = new File(basePath, EduNames.COURSE_META_FILE);
      reader = new InputStreamReader(new FileInputStream(courseMetaFile));
      Gson gson = new GsonBuilder()
        .registerTypeAdapter(Course.class, new StudySerializationUtils.Json.CourseTypeAdapter(courseMetaFile))
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create();
      Course course = gson.fromJson(reader, Course.class);

      if (course == null) {
        Messages.showErrorDialog("This course is incompatible with current version", "Failed to Unpack Course");
        return;
      }

      StudyTaskManager.getInstance(project).setCourse(course);
      File courseDir = new File(OUR_COURSES_DIR, course.getName() + "-" + project.getName());
      course.setCourseDirectory(courseDir.getPath());
      course.setCourseMode(CCUtils.COURSE_MODE);
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
            ApplicationManager.getApplication().runWriteAction(() -> createAnswerFile(project, taskDir, entry));
          }
          taskIndex += 1;
        }
        index += 1;
        taskIndex = 1;
      }
      course.initCourse(true);
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
                                      @NotNull final Map.Entry<String, TaskFile> taskFileEntry) {
    final String name = taskFileEntry.getKey();
    final TaskFile taskFile = taskFileEntry.getValue();
    VirtualFile file = userFileDir.findChild(name);
    assert file != null;
    final Document originDocument = FileDocumentManager.getInstance().getDocument(file);
    if (originDocument == null) {
      return;
    }
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return;

    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> document.replaceString(0, document.getTextLength(), originDocument.getCharsSequence())), "Create answer document", "Create answer document");
    EduDocumentListener listener = new EduDocumentListener(taskFile, false);
    document.addDocumentListener(listener);
    taskFile.sortAnswerPlaceholders();
    for (int i = taskFile.getAnswerPlaceholders().size() - 1; i >= 0; i--) {
      final AnswerPlaceholder answerPlaceholder = taskFile.getAnswerPlaceholders().get(i);
      replaceAnswerPlaceholder(project, document, answerPlaceholder);
    }
    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> FileDocumentManager.getInstance().saveDocument(document)), "x", "qwe");
    document.removeDocumentListener(listener);
  }

  private static void replaceAnswerPlaceholder(@NotNull final Project project,
                                               @NotNull final Document document,
                                               @NotNull final AnswerPlaceholder answerPlaceholder) {
    final int offset = answerPlaceholder.getOffset();
    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      final String text = document.getText(TextRange.create(offset, offset + answerPlaceholder.getRealLength()));
      answerPlaceholder.setTaskText(text);
      answerPlaceholder.init();
      final VirtualFile hints = project.getBaseDir().findChild(EduNames.HINTS);
      if (hints != null) {
        final ArrayList<String> result = new ArrayList<>();
        for (String hint : answerPlaceholder.getHints()) {
          final VirtualFile virtualFile = hints.findChild(hint);
          if (virtualFile != null) {
            final Document hintDocument = FileDocumentManager.getInstance().getDocument(virtualFile);
            if (hintDocument != null) {
              final String hintText = hintDocument.getText();
              result.add(hintText);
            }
          }          
        }
        answerPlaceholder.setHints(result);
      }
      document.replaceString(offset, offset + answerPlaceholder.getRealLength(), answerPlaceholder.getPossibleAnswer());
      answerPlaceholder.setUseLength(false);
      FileDocumentManager.getInstance().saveDocument(document);
    }), "Replace answer placeholder", "From Course Archive");
  }

  private static void synchronize(@NotNull final Project project) {
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
    ProjectView.getInstance(project).refresh();
  }

}