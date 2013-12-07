package org.jetbrains.postfixCompletion.completion
import com.intellij.codeInsight.completion.CompletionAutoPopupTestCase
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.postfixCompletion.settings.PostfixCompletionSettings
import org.jetbrains.postfixCompletion.templates.InstanceofExpressionPostfixTemplate
import org.jetbrains.postfixCompletion.templates.PostfixTemplate
import org.jetbrains.postfixCompletion.templates.SwitchStatementPostfixTemplate

public class TemplatesCompletionTest extends CompletionAutoPopupTestCase {
  public void testDoNotShowTemplateInInappropriateContext() throws Exception {
    doAutoPopupTest("instanceof", null)
  }

  public void testShowTemplateInAutoPopup() throws Exception {
    doAutoPopupTest("instanceof", InstanceofExpressionPostfixTemplate.class)
  }

  public void testShowTemplateOnDoubleLiteral() throws Exception {
    doAutoPopupTest("switch", SwitchStatementPostfixTemplate.class)
  }

  public void testSelectTemplateByTab() throws Exception {
    doCompleteTest("par", '\t' as char)
  }

  public void testSelectTemplateByEnter() throws Exception {
    doCompleteTest("par", '\n' as char)
  }

  public void testQuickTypingWithTab() throws Exception {
    doQuickTypingTest("par", '\t' as char)
  }

  public void testQuickTypingWithEnter() throws Exception {
    doQuickTypingTest("par", '\n' as char)
  }

  public void testDoNotShowDisabledTemplate() throws Exception {
    PostfixCompletionSettings.instance.disableTemplate(new InstanceofExpressionPostfixTemplate())
    doAutoPopupTest("instanceof", null)
  }

  public void testDoNotShowTemplateOnCompletion() throws Exception {
    edt { myFixture.configureByFile(getTestName(true) + ".java") }
    myFixture.completeBasic()
    LookupElement[] elements = myFixture.lookupElements
    assert elements
    assert !ContainerUtil.findInstance(elements, PostfixTemplateLookupElement.class)
  }

  @Override
  public void tearDown() throws Exception {
    PostfixCompletionSettings.instance.templatesState = ContainerUtil.<String, Boolean>newHashMap()
    super.tearDown()
  }

  @Override
  protected String getTestDataPath() {
    return "testData/completion"
  }

  private void doQuickTypingTest(String textToType, char c) {
    edt { myFixture.configureByFile(getTestName(true) + ".java") }
    myFixture.type(textToType + c)
    myFixture.checkResultByFile(getTestName(true) + "_after.java")
  }

  private void doCompleteTest(String textToType, char c) {
    edt { myFixture.configureByFile(getTestName(true) + ".java") }
    type textToType 
    assert lookup
    myFixture.type c 
    myFixture.checkResultByFile(getTestName(true) + "_after.java")
  }

  private void doAutoPopupTest(@NotNull String textToType, @Nullable Class<? extends PostfixTemplate> expectedClass) {
    edt { myFixture.configureByFile(getTestName(true) + ".java") }
    type textToType
    if (expectedClass != null) {
      assert lookup
      LookupElement item = lookup.currentItem
      assert item
      assert item instanceof PostfixTemplateLookupElement
      assertInstanceOf item.postfixTemplate, expectedClass
    }
    else {
      assert !lookup
    }
  }
}
