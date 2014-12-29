import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.actions.CCCreateCourseArchive;
import org.jetbrains.plugins.coursecreator.format.TaskFile;
import org.jetbrains.plugins.coursecreator.format.TaskWindow;

import java.util.List;

public class CCDocumentListenerTest extends CCTestCase {


  //EDU-221
  public void testLongTaskWindows() throws Exception {
    PsiFile psiFile = myFixture.configureByFile("helloworld.py");
    Document document = myFixture.getDocument(psiFile);
    SAXBuilder builder = new SAXBuilder();
    org.jdom.Document xml = builder.build(getTestDataPath() + "/helloworld.xml");
    TaskFile taskFile = XmlSerializer.deserialize(xml, TaskFile.class);
    assertNotNull(taskFile);
    CCCreateCourseArchive.InsertionListener listener = new CCCreateCourseArchive.InsertionListener(taskFile);
    document.addDocumentListener(listener);
    List<TaskWindow> taskWindows = taskFile.getTaskWindows();
    for (TaskWindow tw : taskWindows) {
      replaceTaskWindow(myFixture.getProject(), document, tw);
    }
    assertEquals(5, taskWindows.size());
    checkTaskWindowLocation(document, taskWindows.get(0), 0, taskWindows.get(0).getLength());
    int startOffset = 8 + taskWindows.get(0).getLength();
    checkTaskWindowLocation(document, taskWindows.get(1), startOffset, startOffset + taskWindows.get(1).getLength());
    checkTaskWindowLocation(document, taskWindows.get(4), document.getLineStartOffset(2),
                            document.getLineStartOffset(2) + taskWindows.get(4).getLength());
  }

  private static void checkTaskWindowLocation(Document document, TaskWindow taskWindow, int startOffset, int endOffset) {
    int twStart = taskWindow.getRealStartOffset(document);
    int twEnd = taskWindow.getLength() + twStart;
    assertEquals(startOffset, twStart);
    assertEquals(endOffset, twEnd);
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
}
