package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.lombok.backend.action.lombok.LombokGetterAction;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase;

public class LombokGetterActionTest extends LombokLightActionTestCase {

  @Override
  protected AnAction getAction() {
    return new LombokGetterAction();
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/lombok/getter";
  }

  public void testGetterSimple() throws Exception {
    doTest();
  }

  public void testGetterWithUncommittedDocument() {
    myFixture.configureByText("Test.java", """
      class Test {
        private int value;
      <caret>}
      """);
    var action = getAction();
    var editor = myFixture.getEditor();
    var document = editor.getDocument();
    var dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, getProject())
      .add(CommonDataKeys.EDITOR, editor)
      .build();
    var event = AnActionEvent.createEvent(dataContext, action.getTemplatePresentation().clone(), "", ActionUiKind.NONE, null);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(editor.getCaretModel().getOffset(), """

        public int getValue() {
          return value;
        }
      """));
    assertFalse(PsiDocumentManager.getInstance(getProject()).isCommitted(document));

    action.actionPerformed(event);

    myFixture.checkResult("""
      import lombok.Getter;

      @Getter
      class Test {
        private int value;

      }
      """);
  }

  public void testGetterStatic() throws Exception {
    doTest();
  }

  public void testGetterDifferentVisibility() throws Exception {
    doTest();
  }

  public void testGetterWithAnnotationPresentOnClass() throws Exception {
    doTest();
  }

  public void testGetterWithAnnotationChange() throws Exception {
    doTest();
  }

  public void testGetterWithCustomCode() throws Exception {
    doTest();
  }
}
