// Copyright 2000-2018 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.intellij.plugins.markdown.editor;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class MarkdownPsiElementFactoryTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @Test
  public void testCreateNewCodeFence() {
    doTest("ruby", "a = 1", "```ruby\na = 1\n```");
  }

  @Test
  public void testNoLanguage() {
    doTest(null, "a = 1", "```\na = 1\n```");
  }

  @Test
  public void testEmptyContent() {
    doTest("js", "", "```js\n\n```");
  }

  @Test
  public void testTicks() {
    doTest("ruby", "```", "```ruby\n```");
  }

  @Test
  public void testReference() {
    Pair<PsiElement, PsiElement> codeFence = ReadAction.computeBlocking(
      () -> MarkdownPsiElementFactory.createLinkDeclarationAndReference(codeInsightFixture.get().getProject(), "https://jetbrains.com", "link", "title", "reference"));

    assertNotNull(codeFence.getFirst());
    assertNotNull(codeFence.getSecond());
  }

  @Test
  public void testReferenceWithoutTitle() {
    Pair<PsiElement, PsiElement> codeFence = ReadAction.computeBlocking(
      () -> MarkdownPsiElementFactory.createLinkDeclarationAndReference(codeInsightFixture.get().getProject(), "https://jetbrains.com", "link", null, "reference"));

    assertNotNull(codeFence.getFirst());
    assertNotNull(codeFence.getSecond());
  }

  private void doTest(@Nullable String language, @NotNull String text, @NotNull String expectedText) {
    String actual = ReadAction.computeBlocking(() -> {
      MarkdownCodeFence codeFence = MarkdownPsiElementFactory.createCodeFence(codeInsightFixture.get().getProject(), language, text);
      assertNotNull(codeFence);
      return codeFence.getText();
    });

    assertEquals(actual, expectedText);
  }
}
