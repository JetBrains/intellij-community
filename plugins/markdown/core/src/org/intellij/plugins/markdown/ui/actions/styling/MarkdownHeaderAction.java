// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions.styling;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader;
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil;
import org.intellij.plugins.markdown.util.MarkdownPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static org.intellij.plugins.markdown.lang.MarkdownElementTypes.PARAGRAPH;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets.INLINE_HOLDING_ELEMENT_PARENTS_TYPES;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets.INLINE_HOLDING_ELEMENT_TYPES;

public abstract class MarkdownHeaderAction extends AnAction implements DumbAware {
  /**
   * Returns function that increases or decreases level by 1
   */
  @NotNull
  protected abstract Function<Integer, Integer> getLevelFunction();

  protected abstract boolean isEnabledForCaret(@NotNull PsiFile psiFile, @NotNull Caret caret);

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Editor editor = MarkdownActionUtil.findMarkdownTextEditor(e);
    final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    if (editor == null || psiFile == null || !psiFile.isValid()) {
      return;
    }

    for (Caret caret : ContainerUtil.reverse(editor.getCaretModel().getAllCarets())) {
      if (!isEnabledForCaret(psiFile, caret)) {
        e.getPresentation().setEnabled(false);
        return;
      }
    }
    e.getPresentation().setEnabled(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Editor editor = MarkdownActionUtil.findMarkdownTextEditor(e);
    final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    if (editor == null || psiFile == null) {
      return;
    }

    WriteCommandAction.runWriteCommandAction(psiFile.getProject(), null, null, () -> {
      if (!psiFile.isValid()) {
        return;
      }

      for (Caret caret : ContainerUtil.reverse(editor.getCaretModel().getAllCarets())) {
        PsiElement parent = Objects.requireNonNull(findParent(psiFile, caret));
        MarkdownHeader header = PsiTreeUtil.getParentOfType(parent, MarkdownHeader.class, false);

        if (header != null && header.isValid()) {
          header.replace(createNewLevelHeader(header));
        }
        else {
          parent.replace(createHeaderForText(parent));
        }
      }
    }, psiFile);
  }

  @Nullable
  protected static PsiElement findParent(@NotNull PsiFile psiFile, @NotNull Caret caret) {
    final var elements = MarkdownActionUtil.getElementsUnderCaretOrSelection(psiFile, caret);
    PsiElement first = elements.getFirst();
    PsiElement second = elements.getSecond();
    if (MarkdownPsiUtil.WhiteSpaces.isNewLine(first)) {
      first = PsiTreeUtil.nextVisibleLeaf(first);
    }

    if (MarkdownPsiUtil.WhiteSpaces.isNewLine(second)) {
      second = PsiTreeUtil.prevVisibleLeaf(second);
    }

    if (first == null || second == null || first.getTextOffset() > second.getTextOffset()) {
      return null;
    }

    PsiElement parent = MarkdownActionUtil
      .getCommonParentOfTypes(first, second, TokenSet.orSet(INLINE_HOLDING_ELEMENT_TYPES, INLINE_HOLDING_ELEMENT_PARENTS_TYPES));

    if (parent == null || PsiUtilCore.getElementType(parent) != PARAGRAPH) {
      return parent;
    }

    Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
    assert document != null;


    int startOffset = parent.getTextRange().getStartOffset();
    int endOffset = parent.getTextRange().getEndOffset();
    if (startOffset < 0 || endOffset > document.getTextLength()) {
      return null;
    }

    if (document.getLineNumber(startOffset) == document.getLineNumber(endOffset)) {
      return parent;
    }

    return null;
  }

  private static int sanitizeHeaderLevel(int level) {
    return Math.min(Math.max(level, 0), 6);
  }

  @NotNull
  public MarkdownPsiElement createHeaderForText(@NotNull PsiElement textElement) {
    int level = sanitizeHeaderLevel(getLevelFunction().fun(0));

    return MarkdownPsiElementFactory.createHeader(textElement.getProject(), textElement.getText(), level);
  }

  @NotNull
  public MarkdownPsiElement createNewLevelHeader(@NotNull MarkdownHeader header) {
    int level = sanitizeHeaderLevel(getLevelFunction().fun(Objects.requireNonNull(header).getLevel()));

    MarkdownPsiElement newElement;
    Project project = header.getProject();
    String headerName = Objects.requireNonNull(header.getName());
    if (header.getNode().getElementType() == MarkdownElementTypes.SETEXT_1 && level == 2) {
      newElement = MarkdownPsiElementFactory.createSetext(project, headerName, "-", header.getLastChild().getTextLength());
    }
    else if (header.getNode().getElementType() == MarkdownElementTypes.SETEXT_2 && level == 1) {
      newElement = MarkdownPsiElementFactory.createSetext(project, headerName, "=", header.getLastChild().getTextLength());
    }
    else {
      newElement = level == 0
                   ? MarkdownPsiElementFactory.createTextElement(project, headerName)
                   : MarkdownPsiElementFactory.createHeader(project, headerName, level);
    }
    return newElement;
  }
}