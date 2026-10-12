// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.fileTypes.PlainTextParserDefinition;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ThrowableRunnable;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.assertj.core.api.Assertions.assertThat;

public class LanguageListCompletionTest extends BasePlatformTestCase {

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

  /**
   * Same collapsed-fence shape as {@link #testInSixQuotes()}, but the fence is nested in a list item: the
   * inserted body and closing lines must carry the item's content indent instead of falling back to column 0.
   */
  public void testInSixQuotesIndentedInListItem() {
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
    CodeFenceLanguageProvider.EP_NAME.getPoint().registerExtension(new CodeFenceLanguageProvider() {
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
    configure();
    myFixture.completeBasic();
    assertContainsElements(myFixture.getLookupElementStrings(), "json", "{json is a great lang}");
    myFixture.type("g\t");
    checkResult();
  }

  @TestApplication
  public static class AutopopupTest {
    @SuppressWarnings("deprecation")
    private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
    private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
    private CodeInsightTestFixture myFixture;

    @BeforeEach
    void setUp() {
      myFixture = codeInsightFixture.get();
      // Because injector handles the code in the fence and gets parser definition for that lang
      LanguageParserDefinitions.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, new PlainTextParserDefinition(),
                                                              myFixture.getTestRootDisposable());
    }

    private <E extends Throwable> void runTest(@NotNull ThrowableRunnable<E> runnable) throws E {
      CompletionAutoPopupTestUtil.runWithAutoPopupEnabled(myFixture, runnable);
    }

    private void type(@NotNull String s) {
      CompletionAutoPopupTestUtil.type(myFixture, s);
    }

    private @Nullable LookupImpl getLookup() {
      return CompletionAutoPopupTestUtil.getLookup(myFixture);
    }

    @Test
    public void testAutopopup() {
      runTest(() -> {
        myFixture.configureByText(MarkdownFileType.INSTANCE, "");
        type("```");
        Assertions.assertNotNull(getLookup(), "Lookup should auto-activate");
        myFixture.checkResult("```<caret>```");
        assertThat(getLookup().getItems().stream().map(LookupElement::getLookupString).collect(Collectors.toList())).contains("json");
      });
    }
  }

}
