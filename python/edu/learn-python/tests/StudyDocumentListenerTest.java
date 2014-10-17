import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.edu.StudyDocumentListener;
import com.jetbrains.python.edu.course.TaskFile;
import com.jetbrains.python.edu.course.TaskWindow;

import java.util.ArrayList;
import java.util.List;

public class StudyDocumentListenerTest extends StudyTestCase {

  public void testTaskWindowSimpleUpdate() throws Exception {
    PsiFile psiFile = myFixture.configureByFile("helloworld.py");
    final Document document = myFixture.getDocument(psiFile);

    TaskFile taskFile = new TaskFile();
    List<TaskWindow> taskWindows = new ArrayList<TaskWindow>();
    addTaskWindow(taskWindows, 0, 32, 14);
    taskFile.setTaskWindows(taskWindows);
    StudyDocumentListener listener = new StudyDocumentListener(taskFile);
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
    assertEquals(taskWindows.get(0).getRealStartOffset(document), 36);
  }

  private static void addTaskWindow(List<TaskWindow> taskWindows, int line, int start, int length) {
    TaskWindow taskWindow = new TaskWindow();
    taskWindow.setLine(line);
    taskWindow.setStart(start);
    taskWindow.setLength(length);
    taskWindows.add(taskWindow);
  }
}
