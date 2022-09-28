package org.jetbrains.plugins.textmate.editor;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.textmate.TextMateAcceptanceTestCase;

import java.util.List;

public class TextMateSnippetsCompletionTest extends TextMateAcceptanceTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
  }

  public void testCompletionWithPrefix() {
    myFixture.configureByText("test.md_hack", "s<caret>");
    myFixture.completeBasic();
    assertSameElements(myFixture.getLookupElementStrings(), "select");
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    myFixture
      .checkResult("""
                     <select name="${1:some_name}" id="${2:$1}"${3:${4: multiple}${5: onchange="${6:}"}${7: size="${8:1}"}}>
                     \t<option${9: value="${10:option1}"}>${11:$10}</option>
                     \t<option${12: value="${13:option2}"}>${14:$13}</option>${15:}
                     \t$0
                     </select>""");
  }

  public void testCompletionWithEmptyPrefix() {
    myFixture.configureByText("test.md_hack", "<caret>");
    myFixture.completeBasic();
    assertSameElements(myFixture.getLookupElementStrings(), "!", "!", "!", "!", "!", "!", "!", "!", "div", "doctype", "doctype", "doctype",
                       "doctype", "doctype", "doctype", "doctype", "fieldset", "input", "movie", "opt", "select");
  }

  public void testListTemplatesWithPrefix() {
    myFixture.configureByText("test.md_hack", "fi<caret>");
    new ListTemplatesAction().actionPerformedImpl(myFixture.getProject(), myFixture.getEditor());
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertContainsElements(lookupElementStrings, "fieldset");
    assertDoesntContain(lookupElementStrings, "!", "div", "doctype", "input", "movie", "opt", "select");
    LookupElement[] lookupElements = myFixture.getLookupElements();
    assertNotNull(lookupElements);
    myFixture.getLookup().setCurrentItem(ContainerUtil.find(lookupElements,
                                                            element -> "fieldset".equalsIgnoreCase(element.getLookupString())));
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    myFixture.checkResult("""
                            <fieldset id="${1/[[:alpha:]]+|( )/(?1:_:\\L$0)/g}" ${2:class="${3:}"}>
                            \t<legend>${1:$TM_SELECTED_TEXT}</legend>
                            \t
                            \t$0
                            </fieldset>""");
  }

  public void testListTemplateWithEmptyPrefix() {
    myFixture.configureByText("test.md_hack", "<caret>");
    new ListTemplatesAction().actionPerformedImpl(myFixture.getProject(), myFixture.getEditor());
    assertContainsElements(myFixture.getLookupElementStrings(), "!", "!", "!", "!", "!", "!", "!", "!", "div", "doctype", "doctype",
                           "doctype", "doctype", "doctype", "doctype", "doctype", "fieldset", "input", "movie", "opt", "select");
  }
}
