package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.lombok.backend.action.lombok.LombokGetterAction;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.action.LombokActionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokActionFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestApplication
public class LombokGetterActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokActionFixture> lombokAction =
    lombokActionFixture(projectFixture, "/action/lombok/getter", () -> new LombokGetterAction());

  @Test
  public void testGetterSimple() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testGetterWithUncommittedDocument() {
    var fixture = lombokAction.get().getFixture();
    fixture.configureByText("Test.java", """
      class Test {
        private int value;
      <caret>}
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      var action = new LombokGetterAction();
      var project = fixture.getProject();
      var editor = fixture.getEditor();
      var document = editor.getDocument();
      var dataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.EDITOR, editor)
        .build();
      var event = AnActionEvent.createEvent(dataContext, action.getTemplatePresentation().clone(), "", ActionUiKind.NONE, null);

      WriteCommandAction.runWriteCommandAction(project, () -> document.insertString(editor.getCaretModel().getOffset(), """

          public int getValue() {
            return value;
          }
        """));
      assertFalse(PsiDocumentManager.getInstance(project).isCommitted(document));

      action.actionPerformed(event);

      fixture.checkResult("""
        import lombok.Getter;

        @Getter
        class Test {
          private int value;

        }
        """);
    });
  }

  @Test
  public void testGetterStatic() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testGetterDifferentVisibility() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testGetterWithAnnotationPresentOnClass() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testGetterWithAnnotationChange() throws Exception {
    lombokAction.get().doTest();
  }

  @Test
  public void testGetterWithCustomCode() throws Exception {
    lombokAction.get().doTest();
  }
}
