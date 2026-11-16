package com.intellij.codeInsight.template;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInsight.template.impl.EmptyNode;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class XmlLiveTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private TemplateState getState() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return TemplateManagerImpl.getTemplateState(myFixture.getEditor());
  }

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
  }

  @AfterEach
  void tearDown() {
    EdtTestUtil.runInEdtAndWait(() -> {
      TemplateState state = getState();
      if (state != null) {
        state.gotoEnd();
      }
    });
  }

  @Test
  public void test_tag_template_in_xml() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("frm", "user", "$VAR$");
    template.addVariable("VAR", new EmptyNode(), new ConstantNode("<"), true);

    myFixture.configureByText("a.xml", "<tag><caret></tag>");
    EdtTestUtil.runInEdtAndWait(() -> {
      manager.startTemplate(myFixture.getEditor(), template);
      myFixture.checkResult("<tag><selection><</selection><caret></tag>");
    });
  }

  @Test
  public void test_insert_CDATA_by_CD_and_tab() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.xml", "<tag><caret></tag>");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.type("CD\t"));
    myFixture.checkResult("""
                            <tag><![CDATA[

                            ]]></tag>""");
  }

  @Test
  public void testAvailabilityCDATA() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("CD", "HTML/XML");
    assertTrue(isApplicable("<foo><caret> </foo>", template));
    assertFalse(isApplicable("<foo bar=\"<caret>\"></foo>", template));
  }

  @Test
  public void testAvailabilityT() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("T", "HTML/XML");
    assertTrue(isApplicable("<foo><caret> </foo>", template));
    assertFalse(isApplicable("<foo bar=\"<caret>\"></foo>", template));
  }

  @Test
  public void testQuotedTemplateParametersWithEnforceQuotesOnReformat() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    HtmlCodeStyleSettings settings = getHtmlSettings();
    CodeStyleSettings.QuoteStyle originalQuoteStyle = settings.HTML_QUOTE_STYLE;
    boolean emmetEnabled = EmmetOptions.getInstance().isEmmetEnabled();
    boolean originalEnforceQuotes = settings.HTML_ENFORCE_QUOTES;
    settings.HTML_QUOTE_STYLE = CodeStyleSettings.QuoteStyle.Single;
    settings.HTML_ENFORCE_QUOTES = true;
    EmmetOptions.getInstance().setEmmetEnabled(false);
    try {
      myFixture.configureByText("a.html", "<div><caret></div>");
      EdtTestUtil.runInEdtAndWait(() -> {
        myFixture.type("input:color\t");
        myFixture.type("inputName");
        myFixture.checkResult("<div><input type='color' name='inputName' id=''></div>");
      });
    }
    finally {
      EmmetOptions.getInstance().setEmmetEnabled(emmetEnabled);
      settings.HTML_QUOTE_STYLE = originalQuoteStyle;
      settings.HTML_ENFORCE_QUOTES = originalEnforceQuotes;
    }
  }

  private HtmlCodeStyleSettings getHtmlSettings() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return CodeStyle.getSettings(myFixture.getProject()).getCustomSettings(HtmlCodeStyleSettings.class);
  }

  private boolean isApplicable(String text, TemplateImpl inst) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(XmlFileType.INSTANCE, text);
    return ReadAction.computeBlocking(() -> {
      PsiFile file = myFixture.getFile();
      int offset = myFixture.getEditor().getCaretModel().getOffset();
      return TemplateManagerImpl.isApplicable(inst, TemplateActionContext.expanding(file, offset));
    });
  }
}
