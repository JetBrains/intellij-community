// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ObjectUtils;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.intellij.plugins.markdown.lang.psi.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MarkdownPsiElementFactory {
  private MarkdownPsiElementFactory() { }

  @NotNull
  public static MarkdownFile createFile(@NotNull Project project, @NotNull String text) {
    final LightVirtualFile virtualFile = new LightVirtualFile("temp.rb", MarkdownLanguage.INSTANCE, text);
    PsiFile psiFile = ((PsiFileFactoryImpl)PsiFileFactory.getInstance(project))
      .trySetupPsiForFile(virtualFile, MarkdownLanguage.INSTANCE, true, true);

    if (!(psiFile instanceof MarkdownFile)) {
      throw new RuntimeException("Cannot create a new markdown file. Text: " + text);
    }

    return (MarkdownFile)psiFile;
  }


  @NotNull
  public static MarkdownCodeFenceImpl createCodeFence(@NotNull Project project, @Nullable String language, @NotNull String text) {
    return createCodeFence(project, language, text, null);
  }


  @NotNull
  public static MarkdownCodeFenceImpl createCodeFence(@NotNull Project project,
                                                      @Nullable String language,
                                                      @NotNull String text,
                                                      @Nullable String indent) {
    text = StringUtil.isEmpty(text) ? "" : "\n" + text;
    String content = "```" + StringUtil.notNullize(language) + text + "\n" + StringUtil.notNullize(indent) + "```";
    final MarkdownFile file = createFile(project, content);

    return (MarkdownCodeFenceImpl)file.getFirstChild().getFirstChild();
  }

  @NotNull
  public static MarkdownPsiElement createTextElement(@NotNull Project project, @NotNull String text) {
    return (MarkdownPsiElement)createFile(project, text).getFirstChild().getFirstChild();
  }

  @NotNull
  public static MarkdownHeaderImpl createSetext(@NotNull Project project, @NotNull String text, @NotNull String symbol, int count) {
    return (MarkdownHeaderImpl)createFile(project, text + "\n" + StringUtil.repeat(symbol, count)).getFirstChild().getFirstChild();
  }

  @NotNull
  public static MarkdownHeaderImpl createHeader(@NotNull Project project, @NotNull String text, int level) {
    return (MarkdownHeaderImpl)createFile(project, StringUtil.repeat("#", level) + " " + text).getFirstChild().getFirstChild();
  }

  @NotNull
  public static PsiElement createNewLine(@NotNull Project project) {
    return createFile(project, "\n").getFirstChild().getFirstChild();
  }

  /**
   * Returns pair of the link reference and its declaration
   */
  @NotNull
  public static Pair<PsiElement, PsiElement> createLinkDeclarationAndReference(@NotNull Project project,
                                                                               @NotNull String url,
                                                                               @NotNull String text,
                                                                               @Nullable String title,
                                                                               @NotNull String reference) {
    text = ObjectUtils.notNull(text, reference);
    title = title == null ? "" : " " + title;

    String linkReference = "[" + text + "][" + reference + "]" + "\n\n" + "[" + reference + "]" + ": " + url + title;

    PsiElement linkReferenceElement = createFile(project, linkReference).getFirstChild();

    PsiElement ref = linkReferenceElement.getFirstChild();
    assert ref instanceof MarkdownParagraphImpl;

    PsiElement declaration = linkReferenceElement.getLastChild();
    assert declaration instanceof MarkdownParagraphImpl || declaration instanceof MarkdownLinkDefinitionImpl;

    return Pair.create(ref, declaration);
  }
}