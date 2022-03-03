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

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MarkdownPsiElementFactoryTest extends BasePlatformTestCase {
  public void testCreateNewCodeFence() {
    doTest("ruby", "a = 1", "```ruby\na = 1\n```");
  }

  public void testNoLanguage() {
    doTest(null, "a = 1", "```\na = 1\n```");
  }

  public void testEmptyContent() {
    doTest("js", "", "```js\n```");
  }

  public void testTicks() {
    doTest("ruby", "```", "```ruby\n```");
  }

  public void testReference() {
    Pair<PsiElement, PsiElement> codeFence =
      MarkdownPsiElementFactory.createLinkDeclarationAndReference(myFixture.getProject(), "https://jetbrains.com", "link", "title", "reference");

    assertNotNull(codeFence.getFirst());
    assertNotNull(codeFence.getSecond());
  }

  public void testReferenceWithoutTitle() {
    Pair<PsiElement, PsiElement> codeFence =
      MarkdownPsiElementFactory.createLinkDeclarationAndReference(myFixture.getProject(), "https://jetbrains.com", "link", null, "reference");

    assertNotNull(codeFence.getFirst());
    assertNotNull(codeFence.getSecond());
  }

  private void doTest(@Nullable String language, @NotNull String text, @NotNull String expectedText) {
    MarkdownCodeFence codeFence = MarkdownPsiElementFactory.createCodeFence(myFixture.getProject(), language, text);

    assertNotNull(codeFence);
    assertEquals(codeFence.getText(), expectedText);
  }
}
