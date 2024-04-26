package com.intellij.codeInsight.template;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;

public class XmlLiveTemplateTest extends LightJavaCodeInsightFixtureTestCase {
  private TemplateState getState() {
    return TemplateManagerImpl.getTemplateState(myFixture.getEditor());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (getState() != null) {
        getState().gotoEnd();
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void test_tag_template_in_xml() {
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("frm", "user", "$VAR$");
    template.addVariable("VAR", new EmptyNode(), new ConstantNode("<"), true);

    myFixture.configureByText("a.xml", "<tag><caret></tag>");
    manager.startTemplate(myFixture.getEditor(), template);
    myFixture.checkResult("<tag><selection><</selection><caret></tag>");
  }

  public void test_insert_CDATA_by_CD_and_tab() {
    myFixture.configureByText("a.xml", "<tag><caret></tag>");
    myFixture.type("CD\t");
    myFixture.checkResult("""
                            <tag><![CDATA[

                            ]]></tag>""");
  }

  public void testAvailabilityCDATA() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("CD", "HTML/XML");
    TestCase.assertTrue(isApplicable("<foo><caret> </foo>", template));
    TestCase.assertFalse(isApplicable("<foo bar=\"<caret>\"></foo>", template));
  }

  public void testAvailabilityT() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("T", "HTML/XML");
    TestCase.assertTrue(isApplicable("<foo><caret> </foo>", template));
    TestCase.assertFalse(isApplicable("<foo bar=\"<caret>\"></foo>", template));
  }

  public void testQuotedTemplateParametersWithEnforceQuotesOnReformat() {
    HtmlCodeStyleSettings settings = getHtmlSettings();
    CodeStyleSettings.QuoteStyle originalQuoteStyle = settings.HTML_QUOTE_STYLE;
    boolean emmetEnabled = EmmetOptions.getInstance().isEmmetEnabled();
    boolean originalEnforceQuotes = settings.HTML_ENFORCE_QUOTES;
    settings.HTML_QUOTE_STYLE = CodeStyleSettings.QuoteStyle.Single;
    settings.HTML_ENFORCE_QUOTES = true;
    EmmetOptions.getInstance().setEmmetEnabled(false);
    try {
      myFixture.configureByText("a.html", "<div><caret></div>");
      myFixture.type("input:color\t");
      myFixture.type("inputName");
      myFixture.checkResult("<div><input type='color' name='inputName' id=''></div>");
    }
    finally {
      EmmetOptions.getInstance().setEmmetEnabled(emmetEnabled);
      settings.HTML_QUOTE_STYLE = originalQuoteStyle;
      settings.HTML_ENFORCE_QUOTES = originalEnforceQuotes;
    }
  }

  private HtmlCodeStyleSettings getHtmlSettings() {
    return CodeStyle.getSettings(getProject()).getCustomSettings(HtmlCodeStyleSettings.class);
  }

  private boolean isApplicable(String text, TemplateImpl inst) {
    myFixture.configureByText(XmlFileType.INSTANCE, text);
    PsiFile file = myFixture.getFile();
    int offset = myFixture.getEditor().getCaretModel().getOffset();
    return TemplateManagerImpl.isApplicable(inst, TemplateActionContext.expanding(file, offset));
  }
}
