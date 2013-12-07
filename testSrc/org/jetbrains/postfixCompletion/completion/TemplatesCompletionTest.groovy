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
  public void testDoNotShowTemplateInInappropriateContext() { doAutoPopupTest 'instanceof', null }
  public void testShowTemplateInAutoPopup() { doAutoPopupTest 'instanceof', InstanceofExpressionPostfixTemplate.class }
  public void testShowTemplateOnDoubleLiteral() { doAutoPopupTest 'switch', SwitchStatementPostfixTemplate.class }
  public void testSelectTemplateByTab() { doCompleteTest 'par', '\t' as char }
  public void testSelectTemplateByEnter() { doCompleteTest 'par', '\n' as char }
  public void testQuickTypingWithTab() { doQuickTypingTest 'par', '\t' as char }
  public void testQuickTypingWithEnter() { doQuickTypingTest 'par', '\n' as char }

  public void testDoNotShowDisabledTemplate() {
    PostfixCompletionSettings.instance.disableTemplate new InstanceofExpressionPostfixTemplate()
    doAutoPopupTest 'instanceof', null
  }

  public void testDoNotShowTemplateOnCompletion() {
    edt { myFixture.configureByFile(getTestName(true) + '.java') }
    myFixture.completeBasic()
    LookupElement[] elements = myFixture.lookupElements
    assert elements
    assert !ContainerUtil.findInstance(elements, PostfixTemplateLookupElement.class)
  }

  public void testRecalculatePrefix() {
    edt { myFixture.configureByFile(getTestName(true) + '.java') }
    type 'par'
    myFixture.assertPreferredCompletionItems 1, '.par', 'parents'

    type '\b'
    assert lookup
    myFixture.assertPreferredCompletionItems 0, 'parents'

    type 'r'
    myFixture.assertPreferredCompletionItems 1, '.par', 'parents'
  }

  @Override
  public void tearDown() {
    PostfixCompletionSettings.instance.templatesState = ContainerUtil.<String, Boolean> newHashMap()
    super.tearDown()
  }

  @Override
  protected String getTestDataPath() {
    return 'testData/completion'
  }

  private void doQuickTypingTest(String textToType, char c) {
    edt { myFixture.configureByFile(getTestName(true) + '.java') }
    myFixture.type(textToType + c)
    myFixture.checkResultByFile(getTestName(true) + '_after.java')
  }

  private void doCompleteTest(String textToType, char c) {
    edt { myFixture.configureByFile(getTestName(true) + '.java') }
    type textToType
    assert lookup
    myFixture.type c
    myFixture.checkResultByFile(getTestName(true) + '_after.java')
  }

  private void doAutoPopupTest(@NotNull String textToType, @Nullable Class<? extends PostfixTemplate> expectedClass) {
    edt { myFixture.configureByFile(getTestName(true) + '.java') }
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
