package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.TestActionEvent;
import com.jetbrains.edu.coursecreator.CCTestCase;
import com.jetbrains.edu.coursecreator.CCTestsUtil;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;

import java.util.List;

public class CCShowPreviewTest extends CCTestCase {

  public void testPreviewUnavailable() {
    VirtualFile file = configureByTaskFile("noplaceholders.txt");
    CCShowPreview action = new CCShowPreview();
    TestActionEvent e = getActionEvent(action, PsiManager.getInstance(getProject()).findFile(file));
    action.beforeActionPerformedUpdate(e);
    assertTrue(e.getPresentation().isEnabled() && e.getPresentation().isVisible());
    try {
      action.actionPerformed(e);
      assertTrue("No message shown", false);
    } catch (RuntimeException ex) {
      assertEquals(CCShowPreview.NO_PREVIEW_MESSAGE, ex.getMessage());
    }
  }

  public void testOnePlaceholder() {
    doTest("test");
  }

  public void testSeveralPlaceholders() {
    doTest("several");
  }

  private void doTest(String name) {
    VirtualFile file = configureByTaskFile(name + CCTestsUtil.BEFORE_POSTFIX);
    CCShowPreview action = new CCShowPreview();
    TestActionEvent e = getActionEvent(action,PsiManager.getInstance(getProject()).findFile(file));
    action.beforeActionPerformedUpdate(e);
    assertTrue(e.getPresentation().isEnabled() && e.getPresentation().isVisible());
    action.actionPerformed(e);
    Editor editor = EditorFactory.getInstance().getAllEditors()[1];
    Pair<Document, List<AnswerPlaceholder>> pair = getPlaceholders(name + CCTestsUtil.AFTER_POSTFIX);
    assertEquals("Files don't match", editor.getDocument().getText(), pair.getFirst().getText());
    for (AnswerPlaceholder placeholder : pair.getSecond()) {
      assertNotNull("No highlighter for placeholder:" + CCTestsUtil.getPlaceholderPresentation(placeholder), getHighlighter(editor.getMarkupModel(), placeholder));
    }
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/actions/preview";
  }

  TestActionEvent getActionEvent(AnAction action, PsiFile psiFile) {
    MapDataContext context = new MapDataContext();
    context.put(CommonDataKeys.PSI_FILE, psiFile);
    context.put(CommonDataKeys.PROJECT, getProject());
    context.put(LangDataKeys.MODULE, myFixture.getModule());
    return new TestActionEvent(context, action);
  }
}
