package com.jetbrains.edu.learning;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.EduDocumentListener;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.TaskFile;

import java.util.ArrayList;
import java.util.List;

public class StudyDocumentListenerTest extends StudyTestCase {

  public void testTaskWindowSimpleUpdate() throws Exception {
    PsiFile psiFile = myFixture.configureByFile("helloworld.py");
    final Document document = myFixture.getDocument(psiFile);

    TaskFile taskFile = new TaskFile();
    List<AnswerPlaceholder> answerPlaceholders = new ArrayList<AnswerPlaceholder>();
    addTaskWindow(answerPlaceholders, 0, 32, 14);
    taskFile.setAnswerPlaceholders(answerPlaceholders);
    EduDocumentListener listener = new EduDocumentListener(taskFile);
    document.addDocumentListener(listener);
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.insertString(0, "text");
            FileDocumentManager.getInstance().saveDocument(document);
          }
        });
      }
    }, "x", "qwe");
    assertEquals(answerPlaceholders.get(0).getRealStartOffset(document), 36);
  }

  private static void addTaskWindow(List<AnswerPlaceholder> answerPlaceholders, int line, int start, int length) {
    AnswerPlaceholder answerPlaceholder = new AnswerPlaceholder();
    answerPlaceholder.setLine(line);
    answerPlaceholder.setStart(start);
    answerPlaceholder.setLength(length);
    answerPlaceholders.add(answerPlaceholder);
  }
}
