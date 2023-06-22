// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public abstract class EmmetAbbreviationTestSuite extends TestSuite {
  protected void setUp(@NotNull Project project) throws Exception {
  }

  protected void tearDown() throws Exception {
    EmmetOptions.getInstance().loadState(new EmmetOptions());
  }

  protected void addTestFromJson(String filePath, String... extensions) throws IOException {
    JsonFactory factory = JsonFactory.builder().build();
    JsonParser parser = factory.createParser(new File(filePath));
    parser.enable(JsonParser.Feature.ALLOW_COMMENTS);
    if (parser.nextToken() != JsonToken.START_OBJECT) {
      throw new IOException("Unexpected JSON format");
    }
    while (parser.nextToken() != JsonToken.END_OBJECT) {
      String key = parser.getText();
      parser.nextToken();
      String expected = parser.getText();
      for (String source : StringUtil.split(key, "|")) {
        // replace ${1:hello} with hello
        expected = expected.replaceAll("\\$\\{\\d(:([^}]+))?}", "$2");
        addTest(source, expected, extensions);
      }
    }
    /*
      JsonObject jsonObject = new GsonBuilder().setLenient().create()
        .fromJson(FileUtil.loadFile(), JsonObject.class);
    for (String key : jsonObject.keySet()) {
      for (String source : StringUtil.split(key, "|")) {
        String expected = jsonObject.get(key).getAsString();
        // replace ${1:hello} with hello
        expected = expected.replaceAll("\\$\\{\\d(:([^}]+))?}", "$2");
        addTest(source, expected, extensions);
      }
    }

     */
  }

  protected void addTest(String source, String expected, String... extensions) {
    addTest(source, expected, null, extensions);
  }

  protected void addTest(String source, String expected, @Nullable TestInitializer setUp, String... extensions) {
    for (String extension : extensions) {
      super.addTest(new EmmetAbbreviation(source, expected, extension, false, setUp) {});
    }
  }

  protected void addTestWithPositionCheck(String source, String expected, String... extensions) {
    addTestWithPositionCheck(source, expected, null, extensions);
  }

  protected void addTestWithPositionCheck(String source, String expected, @Nullable TestInitializer setUp, String... extensions) {
    for (String extension : extensions) {
      super.addTest(new EmmetAbbreviation(source, expected, extension, true, setUp) {});
    }
  }

  @Override
  public String getName() {
    return getClass().getName();
  }

  @NotNull
  protected static TestInitializer quoteStyle(CodeStyleSettings.QuoteStyle newStyle) {
    return (fixture, testRootDisposable) -> EmmetAbbreviationTestSuite.getHtmlSettings(fixture).HTML_QUOTE_STYLE = newStyle;
  }

  private static HtmlCodeStyleSettings getHtmlSettings(CodeInsightTestFixture fixture) {
    return CodeStyle.getSettings(fixture.getProject()).getCustomSettings(HtmlCodeStyleSettings.class);
  }

  private abstract class EmmetAbbreviation extends BasePlatformTestCase {
    private final String extension;
    private final String sourceData;
    private final String expectedData;
    private final boolean myCheckPosition;
    private final TestInitializer mySetUp;
    private final Set<Integer> expectedTabStops = new HashSet<>();
    private final Set<Integer> actualTabStops = new HashSet<>();

    EmmetAbbreviation(@NotNull String sourceData,
                      @NotNull String expectedData,
                      @NotNull String extension,
                      boolean checkPosition,
                      @Nullable TestInitializer initializer) {
      this.extension = extension;
      this.sourceData = sourceData;
      this.expectedData = expectedData;
      myCheckPosition = checkPosition;
      mySetUp = initializer;
    }

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      EmmetAbbreviationTestSuite.this.setUp(getProject());
      TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
      if (mySetUp != null) {
        mySetUp.init(myFixture, myFixture.getTestRootDisposable());
      }
    }

    @Override
    protected void tearDown() throws Exception {
      try {
        EmmetAbbreviationTestSuite.this.tearDown();
      }
      catch (Throwable e) {
        addSuppressedException(e);
      }
      finally {
        super.tearDown();
      }
    }

    @Override
    public void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) {
      String expectedText = !expectedData.contains(EditorTestUtil.CARET_TAG) ? prepareExpectedText(expectedData) : expectedData;
      prepareEditorForTest(sourceData, extension);
      expandAndReformat(true);

      myFixture.checkResult(expectedText);
      if (myCheckPosition) {
        assertEquals(expectedTabStops, actualTabStops); //check placeholders
      }
    }

    private void expandAndReformat(final boolean actualCode) {
      Project project = getProject();
      assertNotNull(project);
      EditorAction action = (EditorAction)ActionManager.getInstance().getAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB);
      action.actionPerformed(myFixture.getEditor(), DataManager.getInstance().getDataContext());

      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      UIUtil.dispatchAllInvocationEvents();

      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
        Editor editor = InjectedLanguageUtil.getTopLevelEditor(myFixture.getEditor());
        while (state != null && !state.isFinished()) {
          if (actualCode) {
            actualTabStops.add(editor.getCaretModel().getOffset());
          }
          state.nextTab();
        }
        if (actualCode) {
          actualTabStops.add(editor.getCaretModel().getOffset());
        }
        CodeStyleManager.getInstance(project).reformat(myFixture.getFile());
      });
    }

    private String prepareExpectedText(String expectedData) {
      int index;
      while ((index = expectedData.indexOf('|')) > -1) {
        expectedData = expectedData.substring(0, index) + expectedData.substring(index + 1);
        expectedTabStops.add(index);
      }
      myFixture.configureByText("expected." + extension, expectedData);
      end();
      expandAndReformat(false);
      return myFixture.getFile().getText();
    }

    private void prepareEditorForTest(String sourceText, String extension) {
      myFixture.configureByText("test." + extension, sourceText);
      if (!sourceText.contains(EditorTestUtil.CARET_TAG)) {
        end();
      }
    }

    private void end() {
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
    }

    @Override
    public String toString() {
      return (sourceData + " in " + extension).replace('\n', ' ').replaceAll("  ", "");
    }

    @Override
    public String getName() {
      return toString();
    }

    @NotNull
    @Override
    protected String getTestName(boolean lowercaseFirstLetter) {
      return "";
    }
  }

  public interface TestInitializer {
    void init(CodeInsightTestFixture fixture, Disposable testRootDisposable);
  }
}
