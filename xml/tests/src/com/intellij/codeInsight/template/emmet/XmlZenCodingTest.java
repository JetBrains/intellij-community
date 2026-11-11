package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.HtmlContextType;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.XmlContextType;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated please don't use this class for simple abbreviation expansion tests anymore. It is too complicated.
 * Use {@link EmmetAbbreviationTestFixture} for abbreviation testing.
 */
@Deprecated
public class XmlZenCodingTest extends LightJavaCodeInsightTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  @NonNls static final String BASE_PATH = "/codeInsight/template/emmet/zenCoding/";
  private static final String[] DO_NOT_EXECUTE_IN_COMMAND =
    new String[]{"test2", "testHtml2", "testHtml7", "testHtml8", "testCustomAttrs5", "testNumberInIteration", "testHtml9", "testFormatting",
      "testHtml12"};

  private static boolean startZenCoding(Editor editor, PsiFile file, String abbreviation) {
    try {
      ZenCodingTemplate template = CustomLiveTemplate.EP_NAME.findExtension(ZenCodingTemplate.class);
      if (abbreviation != null && !template.supportsWrapping()) {
        return false;
      }
      if (isApplicable(template, file, editor, abbreviation != null)) {
        final CustomTemplateCallback callback = new CustomTemplateCallback(editor, file);
        if (abbreviation != null) {
          ZenCodingTemplate.doWrap(abbreviation, callback);
        }
        else {
          String key = template.computeTemplateKey(callback);
          if (key != null) {
            template.expand(key, callback);
            return true;
          }
        }
      }
      return false;
    }
    finally {
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }
  }

  private static boolean isApplicable(ZenCodingTemplate template, PsiFile file, Editor editor, boolean wrapping) {
    if (wrapping) {
      CustomTemplateCallback callback = new CustomTemplateCallback(editor, file);
      return template.isApplicable(callback, callback.getOffset(), true);
    }
    return TemplateManagerImpl.isApplicable(template, TemplateActionContext.expanding(file, editor));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
  }

  public void test1() {
    configure("xml");
    doTest();
    checkResult("xml");
    checkPlaceholders(4);
  }

  private void checkPlaceholders(int expCount) {
    TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
    int actualCount = 0;
    while (state != null) {
      state.nextTab();
      state = TemplateManagerImpl.getTemplateState(getEditor());
      actualCount++;
    }
    assertEquals(expCount, actualCount);
  }

  private void doTest() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      getTemplateManager().startTemplate(getEditor(), TemplateSettings.TAB_CHAR);
    });

    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
  }

  @Override
  protected boolean isRunInCommand() {
    return !ArrayUtil.contains(getName(), DO_NOT_EXECUTE_IN_COMMAND);
  }

  public void test2() {
    configure("xml");
    TemplateManager manager = getTemplateManager();
    String group = "user";
    Template template = manager.createTemplate("_t", group, "<$TAG$ $ATTRS$>$END$</$TAG$>");
    template.addVariable("TAG", "", "", true);
    template.addVariable("ATTRS", "", "", false);

    addTemplateAndDoTest(group, template, "xml", () -> {
      type('a');
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      type('b');
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      type('c');
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      assertNull(TemplateManagerImpl.getTemplateState(getEditor()));
    });
  }

  public void test3() {
    configure("xml");
    doTest();
    checkResult("xml");
    checkPlaceholders(0);
  }

  public void test4() {
    configure("xml");
    doTest();
    TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
    assertNotNull(state);
    state.gotoEnd();
    checkResult("xml");
  }

  public void test5() {
    configure("xml");
    doTest();
    checkResult("xml");
    checkPlaceholders(5);
  }

  public void test6() {
    configure("xml");
    final TemplateManager manager = getTemplateManager();
    String group = "user";
    final Template template = manager.createTemplate("aba", group, "<aba attr=\"$END$\"></aba>");
    addTemplateAndDoTest(group, template, "xml", null);
    checkPlaceholders(1);
  }

  public void test7() {
    configure("xml");
    final TemplateManager manager = getTemplateManager();
    String group = "user";
    final Template template = manager.createTemplate("aba", group, "<aba></aba>\n<caba>$END$</caba>");
    addTemplateAndDoTest(group, template, "xml", null);
    checkPlaceholders(0);
  }

  public void test8() {
    configure("xml");
    final TemplateManager manager = getTemplateManager();
    String group = "user";
    final Template template = manager.createTemplate("templ", group, "<templ></templ>");
    addTemplateAndDoTest(group, template, "xml", null);
    checkPlaceholders(0);
  }

  public void test9() {
    configure("xml");
    final TemplateManager manager = getTemplateManager();
    String group = "user";
    final Template template = manager.createTemplate("templ", group, "<templ></templ>");
    addTemplateAndDoTest(group, template, "xml", null);
    checkPlaceholders(1);
  }

  public void testInvalidTagName() {
    configure("xml");
    doTest();
    checkResult("xml");
  }

  public void testNonTagTemplate() {
    configure("xml");
    final TemplateManager manager = getTemplateManager();
    String group = "user";
    final Template template = manager.createTemplate("templ", group, "_templ_");
    addTemplateAndDoTest(group, template, "xml", null);
    checkPlaceholders(0);
  }

  public void testAfterAlphaSymbol1() {
    configure("xhtml");
    doTest();
    checkResult("xhtml");
  }

  public void testAfterAlphaSymbol2() {
    configure("xhtml");
    doTest();
    checkResult("xhtml");
  }

  public void testFormatting() {
    configure("xml");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      TemplateManager manager = getTemplateManager();
      String group = "user";
      TemplateImpl template = (TemplateImpl)manager.createTemplate("entry", group, "<entry type=\"$TYPE$\">$END$</entry>");
      template.addVariable("TYPE", "", "", true);
      CodeInsightTestUtil.addTemplate(template, getTestRootDisposable());
      TemplateContext context = template.getTemplateContext();
      context.setEnabled(TemplateContextTypes.getByClass(XmlContextType.class), true);
    });
    doTest();
    checkResult("xml");
    //noinspection ConstantConditions
    TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
    assertNull(TemplateManagerImpl.getTemplateState(getEditor()));
  }

  public void testHtmlFormatting1() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(1);
  }

  private void configure(String extension) {
    configureByFile(BASE_PATH + getTestName(true) + "." + extension);
  }

  public void testHtmlFormatting2() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(1);
  }

  public void testHtml1() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(0);
  }

  /*public void testHtml3() throws Throwable {
    configureByFile(BASE_PATH + getTestName(false) + ".html");
    final TemplateManager manager = getTemplateManager();
    String group = "user";
    final Template template = manager.createTemplate("_input", group, "<input type=\"$END$\">");
    addTemplatesAndDoTest(group, template);
  }*/

  public void testHtml4() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(1);
  }

  /*public void testHtml5() throws Throwable {
    configureByFile(BASE_PATH + getTestName(false) + ".html");
    final TemplateManager manager = getTemplateManager();
    String group = "user";
    final Template template = manager.createTemplate("_input", group, "<input type=\"$END$\">");
    addTemplatesAndDoTest(group, template);
  }*/

  public void testHtml6() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(2);
  }

  /*public void testHtml7() throws Throwable {
    new WriteCommandAction(getProject()) {
      protected void run(com.intellij.openapi.application.Result result) throws Throwable {
        configureByFile(BASE_PATH + getTestName(false) + ".html");
        TemplateManager manager = getTemplateManager();
        String group = "user";
        Template template = manager.createTemplate("_t", group, "<$TAG$ type=\"$END$\">");
        template.addVariable("TAG", "", "", true);
        addTemplatesAndDoTest(group, template, new Runnable() {
          public void run() {
            type("div1");
            TemplateManagerImpl.getTemplateState(myEditor).nextTab();
            type("div2");
            TemplateManagerImpl.getTemplateState(myEditor).nextTab();
            type("div3");
            TemplateManagerImpl.getTemplateState(myEditor).nextTab();
            assertNull(TemplateManagerImpl.getTemplateState(myEditor));
          }
        });
      }
    }.execute();
  }*/

  public void testHtml8() {
    configure("html");
    addTemplatesAndDoTest(() -> {
      type("1");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      type("2");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      type("3");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      type("4");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      assertNull(TemplateManagerImpl.getTemplateState(getEditor()));
    }, null, "html");

  }

  public void testHtml9() {
    configure("html");
    addTemplatesAndDoTest(() -> {
      type("1");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      type("2");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      assertNull(TemplateManagerImpl.getTemplateState(getEditor()));
    }, null, "html");
  }

  public void testInComment() {
    configure("html");
    doTest();
    checkResult("html");
  }

  public void testInAttrValue() {
    configure("html");
    doTest();
    checkResult("html");
  }

  public void testAutoclosing1() {
    configure("xhtml");
    doTest();
    checkResult("xhtml");
    checkPlaceholders(0);
  }

  public void testAutoclosing2() {
    configure("xhtml");
    doTest();
    checkResult("xhtml");
    checkPlaceholders(0);
  }

  public void testAutoclosing3() {
    configure("xhtml");
    doTest();
    checkResult("xhtml");
  }

  public void testAutoclosing4() {
    configure("html");
    doTest();
    checkResult("html");
  }

  public void testCaretPosition() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(0);
  }

  public void testEmptyHtmlTag() {
    configure("html");
    doTest();
    checkResult("html");
  }

  /*public void testHtml10() throws Throwable {
    configureByFile(BASE_PATH + getTestName(false) + ".html");
    final TemplateManager manager = getTemplateManager();
    String group = "user";
    final Template template = manager.createTemplate("div>div", group, "<div class=\"parent\"><div class=\"child\">$END$</div></div>");
    addTemplatesAndDoTest(group, template);
  }*/

  public void testHtml11() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(1);
  }

  public void testHtml12() {
    configure("html");
    addTemplatesAndDoTest(() -> {
      type("someName");
      //noinspection ConstantConditions
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      assertNull(TemplateManagerImpl.getTemplateState(getEditor()));
    }, null, "html");
  }

  public void testHtml13() {
    configure("html");
    final TemplateManager manager = getTemplateManager();
    String group = "user";
    Template template = manager.createTemplate("_div", group, "<div name=\"$VALUE$\" id=\"$VALUE$\">$END$</div>");
    template.addVariable("VALUE", "", "", true);
    addTemplatesAndDoTest(group, template);
    checkPlaceholders(2);
  }

  public void testHtml14() {
    configure("html");
    addTemplatesAndDoTest(() -> {
      type("1");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      type("2");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      type("3");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      assertNull(TemplateManagerImpl.getTemplateState(getEditor()));
    }, null, "html");
  }

  public void testDefaultHtmlTag1() {
    configure("html");
    final TemplateManager manager = getTemplateManager();
    String group = "user";
    addTemplatesAndDoTest(group, manager.createTemplate("div", group, "<div>$END$</div>"));
  }

  public void testDefaultHtmlTag2() {
    configure("xhtml");
    doTest();
    checkResult("xhtml");
    checkPlaceholders(3);
  }

  public void testDefaultHtmlTag3() {
    configure("html");
    doTest();
    checkResult("html");
  }

  public void testDefaultHtmlTag4() {
    configureByFile(BASE_PATH + "wrapping.html");
    startZenCoding(getEditor(), getFile(), "div*2>.c");
    checkResult("html");
    checkPlaceholders(0);
  }

  public void testDefaultHtmlTag5() {
    configure("xml");
    doTest();
    checkResult("xml");
    checkPlaceholders(0);
  }

  public void testInjectedContext3() {
    MyTestInjector testInjector = new MyTestInjector(getPsiManager());
    testInjector.injectAll(getTestRootDisposable());
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    doTest();
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testFullExpanding() {
    configure("html");
    doTest();
    checkResultByFile(BASE_PATH + getTestName(true) + "_after1.html");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      //noinspection ConstantConditions
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      //noinspection ConstantConditions
      TemplateManagerImpl.getTemplateState(getEditor()).gotoEnd();
      assertNull(TemplateManagerImpl.getTemplateState(getEditor()));
      checkResultByFile(BASE_PATH + getTestName(true) + "_after2.html");
    });
  }

  public void testCustomAttrs1() {
    configure("xml");
    doTest();
    checkResult("xml");
    checkPlaceholders(3);
  }

  public void testCustomAttrs2() {
    configure("html");
    doTest();
    checkResult("html");
  }

  public void testCustomAttrs3() {
    configure("html");
    doTest();
    checkResult("html");
  }

  public void testCustomAttrs4() {
    configure("html");
    doTest();
    checkPlaceholders(1);
    checkResult("html");
  }

  public void testCustomAttrs5() {
    configure("html");
    String group = "user";

    TemplateManager manager = getTemplateManager();
    Template template = manager.createTemplate("_t", group, "<$TAG$ $ATTRS$>$END$</$TAG$>");
    template.addVariable("TAG", "", "", true);
    template.addVariable("ATTRS", "", "", false);

    addTemplateAndDoTest(group, template, "html", () -> {
      type("div1");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      type("div2");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      assertNull(TemplateManagerImpl.getTemplateState(getEditor()));
    });
  }

  public void testCustomAttrs6() {
    configure("html");
    doTest();
    checkResult("html");
  }

  public void testCustomAttrs7() {
    configure("html");
    doTest();
    checkResult("html");
  }

  public void testCustomAttrs8() {
    configure("html");
    doTest();
    checkResult("html");
  }

  public void testCustomAttrs9() {
    configure("html");
    doTest();
    checkResult("html");
  }

  public void testCustomAttrsEmpty1() {
    configure("html");
    doTest();
    checkPlaceholders(1);
    checkResult("html");
  }

  public void testCustomAttrsEmpty2() {
    configure("html");
    doTest();
    checkPlaceholders(1);
    checkResult("html");
  }

  public void testCustomAttrsEmpty3() {
    configure("html");
    doTest();
    checkResult("html");
  }

  public void testFilters1() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(0);
  }

  public void testFilters2() {
    configure("html");
    doTest();
    checkResult("html");
  }

  public void testFilters3() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(1);
  }

  public void testFilters4() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(1);
  }

  public void testFilters5() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(1);
  }

  public void testTrimFilter() {
    configureByFile(BASE_PATH + getTestName(true) + ".html");
    startZenCoding(getEditor(), getFile(), "div*|t");
    checkResultByFile(BASE_PATH + getTestName(true) + "_after.html");
  }

  public void testXslFilter2() {
    configure("xsl");
    doTest();
    checkResult("xsl");
  }

  public void testXslFilter3() {
    configure("xsl");
    doTest();
    checkResult("xsl");
  }

  public void testNumberInIteration1() {
    configure("xml");
    String group = "user";
    TemplateManager manager = getTemplateManager();
    Template tmpl = manager.createTemplate("_t", group, "<$TAG$ $ATTRS$>$END$</$TAG$>");
    tmpl.addVariable("TAG", "", "", true);
    tmpl.addVariable("ATTRS", "", "", false);

    addTemplateAndDoTest(group, tmpl, "xml", () -> {
      type("man");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      type("a");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      type("woman");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      type("b");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      type("man");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      assertNull(TemplateManagerImpl.getTemplateState(getEditor()));
    });
  }

  public void testNumberInIteration2() {
    configure("xml");
    doTest();
    checkResult("xml");
    checkPlaceholders(2);
  }

  public void testMulWithoutNumber1() {
    configure("xml");
    doTest();
    checkResult("xml");
    checkPlaceholders(0);
  }

  public void testMulWithoutNumber2() {
    configure("xml");
    doTest();
    checkResult("xml");
  }

  public void testInsideHtmlTag() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(1);
  }

  public void testEmptyFile() {
    configure("html");
    doTest();
    checkResult("html");
  }

  public void testEndOfFile() {
    configure("html");
    doTest();
    checkResult("html");
  }

  public void testMultipleAttrs() {
    configure("xml");
    doTest();
    checkResult("xml");
  }

  public void testMultipleAttrs1() {
    configure("xml");
    doTest();
    checkResult("xml");
  }

  private void checkResult(String extension) {
    checkResultByFile(BASE_PATH + getTestName(true) + "_after." + extension);
  }

  public void testBigHtmlFile() {
    configure("html");
    doTest();
    TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
    assertNotNull(state);
    state.gotoEnd();
    checkResult("html");
  }

  public void testBraces() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(7);
  }

  /*public void testInsideStyleAttribute() throws Throwable {
    configureByFile(BASE_PATH + getTestName(false) + ".html");
    final TemplateManager manager = getTemplateManager();
    String group = "user";
    final Template template = manager.createTemplate("b:a", group, "bottom:auto;$END$");
    addTemplatesAndDoTest(group, template);
  }*/

  public void testIncorrectFilter() {
    configure("html");
    doTest();
    checkResult("html");
  }

  public void testAmbiguousTemplate1() {
    configure("html");
    final TemplateManager manager = getTemplateManager();
    final Template template1 = manager.createTemplate("b", "group1", "<b></b>");
    final Template template2 = manager.createTemplate("b", "group2", "<b></b>");
    addTemplatesAndDoTest(null, null, "html", Pair.create("group1", template1),
                          Pair.create("group2", template2));
  }

  public void testAmbiguousTemplate2() {
    configure("html");
    final TemplateManager manager = getTemplateManager();
    final Template template1 = manager.createTemplate("b", "group1", "<b></b>");
    final Template template2 = manager.createTemplate("b", "group2", "<b></b>");
    addTemplatesAndDoTest(null, null, "html", Pair.create("group1", template1),
                          Pair.create("group2", template2));
  }

  public void testWrapping1() {
    configureByFile(BASE_PATH + "wrapping.html");
    addTemplatesAndDoTest(() -> {
      type("1");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      assertNull(TemplateManagerImpl.getTemplateState(getEditor()));
    }, "div>a", "html");
  }

  public void testWrapping2() {
    configureByFile(BASE_PATH + "wrapping.html");
    addTemplatesAndDoTest(() -> {
      type("1");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      type("2");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      type("3");
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      assertNull(TemplateManagerImpl.getTemplateState(getEditor()));
    }, "div*3>a", "html");
  }

  public void testWrapping3() {
    configureByFile(BASE_PATH + "wrapping3.html");
    startZenCoding(getEditor(), getFile(), "div#id-$*2>div.class-$*2>div+div");
    checkResult("html");
    checkPlaceholders(3);
  }

  public void testWrapping4() {
    configure("html");
    addTemplatesAndDoTest(() -> {
      type("1");
      //noinspection ConstantConditions
      TemplateManagerImpl.getTemplateState(getEditor()).nextTab();
      assertNull(TemplateManagerImpl.getTemplateState(getEditor()));
    }, "a", "html");
  }

  public void testWrapping5() {
    configureByFile(BASE_PATH + "wrapping5.html");
    startZenCoding(getEditor(), getFile(), "div>div");
    checkResult("html");
    checkPlaceholders(0);
  }

  public void testWrapping6() {
    configureByFile(BASE_PATH + "wrapping6.html");
    startZenCoding(getEditor(), getFile(), "div>div");
    checkResult("html");
    checkPlaceholders(0);
  }

  public void testWrapping7() {
    configureByFile(BASE_PATH + "wrapping7.html");
    startZenCoding(getEditor(), getFile(), ".container");
    checkResult("html");
    checkPlaceholders(0);
  }

  public void testWrapping8() {
    configureByFile(BASE_PATH + "wrapping8.html");
    startZenCoding(getEditor(), getFile(), ".item");
    checkResult("html");
    checkPlaceholders(0);
  }

  public void testWrapEachLine1() {
    configureByFile(BASE_PATH + "wrapEachLine.html");
    addTemplatesAndDoTest(null, "div>li*>a", "html");
    checkResultByFile(BASE_PATH + "wrapEachLine1_after.html");
  }

  public void testWrapEachLine2() {
    configureByFile(BASE_PATH + "wrapEachLine.html");
    startZenCoding(getEditor(), getFile(), "div>li*>div.class$+div");
    checkResultByFile(BASE_PATH + "wrapEachLine2_after.html");
    checkPlaceholders(1);
  }

  public void testWrapEachLine3() {
    configureByFile(BASE_PATH + "wrapEachLine.html");
    startZenCoding(getEditor(), getFile(), "(div+div+ul>li)*");
    checkResultByFile(BASE_PATH + "wrapEachLine3_after.html");
    checkPlaceholders(3);
  }

  public void testWrapEachLineTrim() {
    configureByFile(BASE_PATH + "wrapEachLineTrim.html");
    startZenCoding(getEditor(), getFile(), "div*");
    checkResultByFile(BASE_PATH + "wrapEachLineTrim_after.html");
    checkPlaceholders(0);
  }

  public void testWrapEachLineTrim1() {
    configureByFile(BASE_PATH + "wrapEachLineTrim1.html");
    startZenCoding(getEditor(), getFile(), "ul>li*>a");
    checkResultByFile(BASE_PATH + "wrapEachLineTrim1_after.html");
    checkPlaceholders(4);
  }

  public void testInAttribute() {
    configureByFile(BASE_PATH + getTestName(false) + ".html");
    doTest();
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.html");
    checkPlaceholders(0);
  }

  public void testInAttribute1() {
    configureByFile(BASE_PATH + getTestName(false) + ".html");
    doTest();
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.html");
    checkPlaceholders(0);
  }

  public void testTextNodes1() {
    configure("xml");
    doTest();
    checkResult("xml");
    checkPlaceholders(0);
  }

  public void testTextNodes2() {
    configure("xml");
    doTest();
    checkResult("xml");
    checkPlaceholders(0);
  }

  public void testTextNodes3() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(1);
  }

  public void testTextNodes4() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(1);
  }

  public void testSurroundedTextMarker1() {
    configureByFile(BASE_PATH + "surroundedTextMarker.html");
    startZenCoding(getEditor(), getFile(), "ul>li*>a{Click at $#}");
    checkResult("html");
    checkPlaceholders(3);
  }

  public void testSurroundedTextMarker2() {
    configureByFile(BASE_PATH + "surroundedTextMarker.html");
    startZenCoding(getEditor(), getFile(), "span[title=$#]{title is $#}*");
    checkResult("html");
    checkPlaceholders(0);
  }

  public void testSurroundedTextMarker3() {
    configureByFile(BASE_PATH + "surroundedTextMarker.html");
    startZenCoding(getEditor(), getFile(), "ul>li*>span{$#}+{ }+a[title=$#]{$#}");
    checkResult("html");
    checkPlaceholders(3);
  }
  public void testSurroundedTextMarker4() {
    configureByFile(BASE_PATH + "surroundedTextMarker.html");
    startZenCoding(getEditor(), getFile(), "a[href=$#]*");
    checkResult("html");
    checkPlaceholders(2);
  }

  public void testSurroundedTextMarker5() {
    configureByFile(BASE_PATH + "surroundedTextMarker.html");
    startZenCoding(getEditor(), getFile(), "div[class=\"class $#\"]*>a");
    checkResult("html");
    checkPlaceholders(5);
  }

  public void testIncorrectAbbreviations() {
    configureByFile(BASE_PATH + "surroundedTextMarker.html");
    final CustomTemplateCallback callback = new CustomTemplateCallback(getEditor(), getFile());

    assertFalse(ZenCodingTemplate.checkTemplateKey("div[name=\"a", callback));
    assertFalse(ZenCodingTemplate.checkTemplateKey("div[name='a", callback));
    assertFalse(ZenCodingTemplate.checkTemplateKey("div{txt", callback));
  }

  public void testWi9889() {
    configure("html");
    doTest();
    checkResult("html");
    checkPlaceholders(3);
  }
  
  public void testDoNotProcessInvalidAttributes() {
    Template template = TemplateManager.getInstance(getProject()).createTemplate("test", "user", """
      <inp2:m_if check="$check$"$params$>
          $if_code$
      </inp2:m_if>
      $END$""");
    configure("html");
    addTemplatesAndDoTest("user", template);
  }

  private void addTemplatesAndDoTest(String group, Template template) {
    addTemplateAndDoTest(group, template, "html", null);
  }

  private void addTemplateAndDoTest(String group, Template template, String extension, Runnable actionAfterStart) {
    addTemplatesAndDoTest(actionAfterStart, null, extension, Pair.create(group, template));
  }

  @SafeVarargs
  private void addTemplatesAndDoTest(Runnable actionAfterStart,
                                     String wrappingAbbreviation,
                                     String extension,
                                     Pair<String, Template>... groupAndTemplates) {
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      for (Pair<String, Template> groupAndTemplate : groupAndTemplates) {
        Template template = groupAndTemplate.second;
        TemplateImpl templateImpl = (TemplateImpl)template;
        TemplateContext context = templateImpl.getTemplateContext();

        context.setEnabled(TemplateContextTypes.getByClass(HtmlContextType.class), true);
        context.setEnabled(TemplateContextTypes.getByClass(XmlContextType.class), true);
        templateImpl.setToReformat(true);
        CodeInsightTestUtil.addTemplate(template, getTestRootDisposable());
      }
    });
    final Editor editor = getEditor();
    if (wrappingAbbreviation != null) {
      startZenCoding(editor, getFile(), wrappingAbbreviation);
    }
    else {
      doTest();
    }

    if (actionAfterStart != null) {
      WriteCommandAction.runWriteCommandAction(getProject(), actionAfterStart);
    }

    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    checkResult(extension);
  }

  private TemplateManager getTemplateManager() {
    return TemplateManager.getInstance(getProject());
  }
}
