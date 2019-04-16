package com.intellij.bash.template;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;

public class BashLiveTemplateTest extends BashBaseFixtureTestCase {

  private Set<String> BASH_LIVE_TEMPLATES = ContainerUtil.newHashSet(
      "bash", "sh", "zsh", "fori"
  );

  @Override
  protected String getDataPath() {
    return "testData/liveTemplates";
  }

  public void testBashShebang() {
    doTest();
  }

  public void testShShebang() {
    doTest();
  }

  public void testZshShebang() {
    doTest();
  }

  public void testForiExpression() {
    doTest();
  }

  private void doTest() {
    configureByFile();

    final Editor editor = myFixture.getEditor();
    final Project project = editor.getProject();
    assertNotNull(project);
    new ListTemplatesAction().actionPerformedImpl(project, editor);
    final LookupImpl lookup = (LookupImpl) LookupManager.getActiveLookup(editor);
    assertNotNull(lookup);
    final List<LookupElement> elements = lookup.getItems().stream()
        .filter(item -> BASH_LIVE_TEMPLATES.contains(item.getLookupString()))
        .collect(toList());
    assertNotEmpty(elements);
    lookup.setCurrentItem(elements.get(0));
    lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    TemplateState template = TemplateManagerImpl.getTemplateState(editor);
    if (template != null) {
      Disposer.dispose(template);
    }
    WriteCommandAction.runWriteCommandAction(null, () -> {
      CodeStyleManager.getInstance(myFixture.getProject()).reformat(myFixture.getFile());
    });
    myFixture.getEditor().getSelectionModel().removeSelection();
    myFixture.checkResultByFile(getAfterTestName());
  }
}