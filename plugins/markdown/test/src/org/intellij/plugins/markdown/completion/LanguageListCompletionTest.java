// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.completion;

import com.intellij.codeInsight.completion.CompletionAutoPopupTestCase;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.fileTypes.PlainTextParserDefinition;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider;
import org.intellij.plugins.markdown.injection.LanguageGuesser;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LanguageListCompletionTest extends LightPlatformCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/completion/languageList/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // Because injector handles the code in the fence and gets parser definition for that lang
    LanguageParserDefinitions.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, new PlainTextParserDefinition(),
                                                            getTestRootDisposable());
  }

  private void doTest(@NotNull String toType) {
    myFixture.testCompletionTyping(getTestName(true) + ".md", toType, getTestName(true) + "_after.md");
  }

  private void configure() {
    myFixture.configureByFile(getTestName(true) + ".md");
  }

  private void checkResult() {
    myFixture.checkResultByFile(getTestName(true) + "_after.md");
  }

  private void checkEmptyCompletion() {
    myFixture.testCompletionVariants(getTestName(true) + ".md");
  }

  public void testExistingFence() {
    doTest("jso\n");
  }

  public void testExistingFenceTab() {
    configure();
    myFixture.completeBasic();
    assertContainsElements(myFixture.getLookupElementStrings(), "json");
    myFixture.type("so\t");
    checkResult();
  }

  public void testExistingNotFence() {
    checkEmptyCompletion();
  }

  public void testInSixQuotes() {
    doTest("jso\n");
  }

  public void testInSixQuotesNotMiddle() {
    checkEmptyCompletion();
  }

  public void testInSixQuotesTab() {
    configure();
    myFixture.completeBasic();
    assertContainsElements(myFixture.getLookupElementStrings(), "json");
    myFixture.type("so\t");
    checkResult();
  }

  public void testCustomCompletionProvider() {
    try {
      CodeFenceLanguageProvider.EP_NAME.getPoint(null).registerExtension(new CodeFenceLanguageProvider() {
          @Nullable
          @Override
          public Language getLanguageByInfoString(@NotNull String infoString) {
            return null;
          }

          @NotNull
          @Override
          public List<LookupElement> getCompletionVariantsForInfoString(@NotNull CompletionParameters parameters) {
            return Collections.singletonList(LookupElementBuilder.create("{json is a great lang}")
            .withInsertHandler((context, item) -> {
              context.getDocument().insertString(context.getEditor().getCaretModel().getOffset(), "Customized insertion");
              context.getEditor().getCaretModel().moveCaretRelatively("Customized insertion".length(), 0, true, false, true);
            }));
          }
        }, myFixture.getTestRootDisposable());

      LanguageGuesser.INSTANCE.resetCodeFenceLanguageProviders();
      configure();
      myFixture.completeBasic();
      assertContainsElements(myFixture.getLookupElementStrings(), "json", "{json is a great lang}");
      myFixture.type("g\t");
      checkResult();
    }
    finally {
      LanguageGuesser.INSTANCE.resetCodeFenceLanguageProviders();
    }
  }

  public static class AutopopupTest extends CompletionAutoPopupTestCase {

    @Override
    protected void setUp() throws Exception {
      super.setUp();
      // Because injector handles the code in the fence and gets parser definition for that lang
      LanguageParserDefinitions.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, new PlainTextParserDefinition(),
                                                              getTestRootDisposable());
    }

    public void testAutopopup() {
      myFixture.configureByText(MarkdownFileType.INSTANCE, "");
      type("```");
      assertNotNull("Lookup should auto-activate", getLookup());
      myFixture.checkResult("```<caret>```");
      assertContainsElements(getLookup().getItems().stream().map(LookupElement::getLookupString).collect(Collectors.toList()), "json");
    }
  }

}
