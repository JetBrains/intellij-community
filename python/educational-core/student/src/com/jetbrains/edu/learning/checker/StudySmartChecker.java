package com.jetbrains.edu.learning.checker;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.EduDocumentListener;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.StudyStatus;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class StudySmartChecker {
  private StudySmartChecker() {

  }

  private static final Logger LOG = Logger.getInstance(StudySmartChecker.class);

  public static void smartCheck(@NotNull final AnswerPlaceholder placeholder,
                                @NotNull final Project project,
                                @NotNull final VirtualFile answerFile,
                                @NotNull final TaskFile answerTaskFile,
                                @NotNull final TaskFile usersTaskFile,
                                @NotNull final StudyTestRunner testRunner,
                                @NotNull final VirtualFile virtualFile,
                                @NotNull final Document usersDocument) {

    try {
      final int index = placeholder.getIndex();
      String windowCopyName = answerFile.getNameWithoutExtension() + index + EduNames.WINDOW_POSTFIX + answerFile.getExtension();
      final VirtualFile windowCopy =
        answerFile.copy(project, answerFile.getParent(), windowCopyName);
      final FileDocumentManager documentManager = FileDocumentManager.getInstance();
      final Document windowDocument = documentManager.getDocument(windowCopy);
      if (windowDocument != null) {
        final File resourceFile =
          StudyUtils.copyResourceFile(virtualFile.getName(), windowCopy.getName(), project, usersTaskFile.getTask());
        final TaskFile windowTaskFile = new TaskFile();
        TaskFile.copy(answerTaskFile, windowTaskFile);
        EduDocumentListener listener = new EduDocumentListener(windowTaskFile);
        windowDocument.addDocumentListener(listener);
        int start = placeholder.getRealStartOffset(windowDocument);
        int end = start + placeholder.getLength();
        final AnswerPlaceholder userAnswerPlaceholder = usersTaskFile.getAnswerPlaceholders().get(placeholder.getIndex());
        int userStart = userAnswerPlaceholder.getRealStartOffset(usersDocument);
        int userEnd = userStart + userAnswerPlaceholder.getLength();
        String text = usersDocument.getText(new TextRange(userStart, userEnd));
        windowDocument.replaceString(start, end, text);
        ApplicationManager.getApplication().runWriteAction(() -> {
          documentManager.saveDocument(windowDocument);
        });
        VirtualFile fileWindows = EduUtils.flushWindows(windowTaskFile, windowCopy, true);
        Process smartTestProcess = testRunner.createCheckProcess(project, windowCopy.getPath());
        final CapturingProcessHandler handler = new CapturingProcessHandler(smartTestProcess, null, windowCopy.getPath());
        final ProcessOutput output = handler.runProcess();
        boolean res = StudyTestsOutputParser.getTestsOutput(output).isSuccess();
        StudyTaskManager.getInstance(project).setStatus(userAnswerPlaceholder, res ? StudyStatus.Solved : StudyStatus.Failed);
        StudyUtils.deleteFile(windowCopy);
        if (fileWindows != null) {
          StudyUtils.deleteFile(fileWindows);
        }
        if (!resourceFile.delete()) {
          LOG.error("failed to delete", resourceFile.getPath());
        }
      }
    }
    catch (ExecutionException | IOException e) {
      LOG.error(e);
    }
  }
}
