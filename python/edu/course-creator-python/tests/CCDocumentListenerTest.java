import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.xmlb.XmlSerializer;
import com.jetbrains.edu.coursecreator.actions.CCCreateCourseArchive;
import com.jetbrains.edu.coursecreator.format.TaskFile;
import com.jetbrains.edu.coursecreator.format.AnswerPlaceholder;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;

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
    List<AnswerPlaceholder> answerPlaceholders = taskFile.getTaskWindows();
    for (AnswerPlaceholder tw : answerPlaceholders) {
      replaceTaskWindow(myFixture.getProject(), document, tw);
    }
    assertEquals(5, answerPlaceholders.size());
    checkTaskWindowLocation(document, answerPlaceholders.get(0), 0, answerPlaceholders.get(0).getLength());
    int startOffset = 8 + answerPlaceholders.get(0).getLength();
    checkTaskWindowLocation(document, answerPlaceholders.get(1), startOffset, startOffset + answerPlaceholders.get(1).getLength());
    checkTaskWindowLocation(document, answerPlaceholders.get(4), document.getLineStartOffset(2),
                            document.getLineStartOffset(2) + answerPlaceholders.get(4).getLength());
  }

  private static void checkTaskWindowLocation(Document document, AnswerPlaceholder answerPlaceholder, int startOffset, int endOffset) {
    int twStart = answerPlaceholder.getRealStartOffset(document);
    int twEnd = answerPlaceholder.getLength() + twStart;
    assertEquals(startOffset, twStart);
    assertEquals(endOffset, twEnd);
  }


  private static void replaceTaskWindow(@NotNull final Project project,
                                        @NotNull final Document document,
                                        @NotNull final AnswerPlaceholder answerPlaceholder) {
    final String taskText = answerPlaceholder.getTaskText();
    final int lineStartOffset = document.getLineStartOffset(answerPlaceholder.getLine());
    final int offset = lineStartOffset + answerPlaceholder.getStart();
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.replaceString(offset, offset + answerPlaceholder.getReplacementLength(), taskText);
            FileDocumentManager.getInstance().saveDocument(document);
          }
        });
      }
    }, "x", "qwe");
  }
}
