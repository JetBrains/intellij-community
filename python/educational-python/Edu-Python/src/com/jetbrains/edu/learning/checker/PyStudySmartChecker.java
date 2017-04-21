package com.jetbrains.edu.learning.checker;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduDocumentListener;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

class PyStudySmartChecker {
  private PyStudySmartChecker() {

  }

  private static final Logger LOG = Logger.getInstance(PyStudySmartChecker.class);

  private static void smartCheck(@NotNull final AnswerPlaceholder placeholder,
                                @NotNull final Project project,
                                @NotNull final VirtualFile answerFile,
                                @NotNull final TaskFile answerTaskFile,
                                @NotNull final TaskFile usersTaskFile,
                                @NotNull final PyStudyTestRunner testRunner,
                                @NotNull final VirtualFile virtualFile,
                                @NotNull final Document usersDocument) {
    VirtualFile fileWindows = null;
    VirtualFile windowCopy = null;
    try {
      final int index = placeholder.getIndex();
      String windowCopyName = answerFile.getNameWithoutExtension() + index + EduNames.WINDOW_POSTFIX + answerFile.getExtension();
      windowCopy = answerFile.copy(project, answerFile.getParent(), windowCopyName);
      final FileDocumentManager documentManager = FileDocumentManager.getInstance();
      final Document windowDocument = documentManager.getDocument(windowCopy);
      if (windowDocument != null) {
        TaskFile windowTaskFile = answerTaskFile.getTask().copy().getTaskFile(StudyUtils.pathRelativeToTask(virtualFile));
        if (windowTaskFile == null) {
          return;
        }
        EduDocumentListener listener = new EduDocumentListener(windowTaskFile);
        windowDocument.addDocumentListener(listener);
        int start = placeholder.getOffset();
        int end = start + placeholder.getRealLength();
        final AnswerPlaceholder userAnswerPlaceholder = usersTaskFile.getAnswerPlaceholders().get(placeholder.getIndex());
        int userStart = userAnswerPlaceholder.getOffset();
        int userEnd = userStart + userAnswerPlaceholder.getRealLength();
        String text = usersDocument.getText(new TextRange(userStart, userEnd));
        windowDocument.replaceString(start, end, text);
        ApplicationManager.getApplication().runWriteAction(() -> documentManager.saveDocument(windowDocument));
        fileWindows = EduUtils.flushWindows(windowTaskFile, windowCopy);
        Process smartTestProcess = testRunner.createCheckProcess(project, windowCopy.getPath());
        final CapturingProcessHandler handler = new CapturingProcessHandler(smartTestProcess, null, windowCopy.getPath());
        final ProcessOutput output = handler.runProcess();
        final Course course = StudyTaskManager.getInstance(project).getCourse();
        if (course != null) {
          boolean res = StudyTestsOutputParser.getTestsOutput(output, course.isAdaptive()).isSuccess();
          StudyTaskManager.getInstance(project).setStatus(userAnswerPlaceholder, res ? StudyStatus.Solved : StudyStatus.Failed);
        }
      }
    }
    catch (ExecutionException | IOException e) {
      LOG.error(e);
    }
    finally {
      StudyUtils.deleteFile(windowCopy);
      StudyUtils.deleteFile(fileWindows);
    }
  }

  static void runSmartTestProcess(@NotNull final VirtualFile taskDir,
                                         @NotNull final PyStudyTestRunner testRunner,
                                         @NotNull final String taskFileName,
                                         @NotNull final TaskFile taskFile,
                                         @NotNull final Project project) {
    final VirtualFile virtualFile = taskDir.findFileByRelativePath(taskFileName);
    if (virtualFile == null) {
      return;
    }
    Pair<VirtualFile, TaskFile> pair = getCopyWithAnswers(taskDir, virtualFile, taskFile);
    if (pair == null) {
      return;
    }
    VirtualFile answerFile = pair.getFirst();
    TaskFile answerTaskFile = pair.getSecond();
    try {
      for (final AnswerPlaceholder answerPlaceholder : answerTaskFile.getActivePlaceholders()) {
        final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
        if (document == null) {
          continue;
        }
        smartCheck(answerPlaceholder, project, answerFile, answerTaskFile, taskFile, testRunner,
                   virtualFile, document);
      }
    }
    finally {
      StudyUtils.deleteFile(answerFile);
    }
  }

  static Pair<VirtualFile, TaskFile> getCopyWithAnswers(@NotNull final VirtualFile taskDir,
                                                                @NotNull final VirtualFile file,
                                                                @NotNull final TaskFile source) {
    try {
      VirtualFile answerFile = file.copy(taskDir, taskDir, file.getNameWithoutExtension() + EduNames.ANSWERS_POSTFIX + "." + file.getExtension());
      final FileDocumentManager documentManager = FileDocumentManager.getInstance();
      final Document document = documentManager.getDocument(answerFile);
      if (document != null) {
        TaskFile answerTaskFile = source.getTask().copy().getTaskFile(StudyUtils.pathRelativeToTask(file));
        if (answerTaskFile == null) {
          return null;
        }
        EduDocumentListener listener = new EduDocumentListener(answerTaskFile);
        document.addDocumentListener(listener);
        for (AnswerPlaceholder answerPlaceholder : answerTaskFile.getActivePlaceholders()) {
          final int start = answerPlaceholder.getOffset();
          final int end = start + answerPlaceholder.getRealLength();
          final String text = answerPlaceholder.getPossibleAnswer();
          document.replaceString(start, end, text);
        }
        ApplicationManager.getApplication().runWriteAction(() -> documentManager.saveDocument(document));
        return Pair.create(answerFile, answerTaskFile);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }
}
