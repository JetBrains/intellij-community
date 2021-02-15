// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.intellij.plugins.markdown.ui.split.SplitFileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MarkdownActionUtil {
  @Nullable
  public static SplitFileEditor findSplitEditor(AnActionEvent e) {
    final FileEditor editor = e.getData(PlatformDataKeys.FILE_EDITOR);
    return findSplitEditor(editor);
  }

  @Nullable
  public static SplitFileEditor findSplitEditor(@Nullable FileEditor editor) {
    if (editor instanceof SplitFileEditor) {
      return (SplitFileEditor)editor;
    }
    else {
      return SplitFileEditor.PARENT_SPLIT_KEY.get(editor);
    }
  }

  @Nullable
  public static Editor findMarkdownTextEditor(AnActionEvent e) {
    final SplitFileEditor splitEditor = findSplitEditor(e);
    if (splitEditor == null) {
      // This fallback is used primarily for testing

      final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
      if (psiFile != null && psiFile.getLanguage() == MarkdownLanguage.INSTANCE && ApplicationManager.getApplication().isUnitTestMode()) {
        return e.getData(CommonDataKeys.EDITOR);
      }
      else {
        return null;
      }
    }

    if (!(splitEditor.getMainEditor() instanceof TextEditor)) {
      return null;
    }
    final TextEditor mainEditor = (TextEditor)splitEditor.getMainEditor();
    if (!mainEditor.getComponent().isVisible()) {
      return null;
    }

    return mainEditor.getEditor();
  }

  @Nullable
  public static Couple<PsiElement> getElementsUnderCaretOrSelection(@NotNull PsiFile file, @NotNull Caret caret) {
    if (caret.getSelectionStart() == caret.getSelectionEnd()) {
      final PsiElement element = file.findElementAt(caret.getSelectionStart());
      if (element == null) {
        return null;
      }
      return Couple.of(element, element);
    }
    else {
      final PsiElement startElement = file.findElementAt(caret.getSelectionStart());
      final PsiElement endElement = file.findElementAt(caret.getSelectionEnd());
      if (startElement == null || endElement == null) {
        return null;
      }
      return Couple.of(startElement, endElement);
    }
  }

  @Nullable
  public static PsiElement getCommonParentOfType(@NotNull PsiElement element1,
                                                 @NotNull PsiElement element2,
                                                 @NotNull final IElementType elementType) {
    return getCommonParentOfTypes(element1, element2, TokenSet.create(elementType));
  }

  @Nullable
  public static PsiElement getCommonTopmostParentOfTypes(@NotNull PsiElement element1,
                                                         @NotNull PsiElement element2,
                                                         @NotNull TokenSet tokenSet) {
    final PsiElement base = PsiTreeUtil.findCommonParent(element1, element2);
    return getTopmostParentOfType(base, (Condition<? super PsiElement>)element -> {
      final ASTNode node = element.getNode();
      return node != null && tokenSet.contains(node.getElementType());
    });
  }

  @Nullable
  public static PsiElement getTopmostParentOfType(@Nullable PsiElement element, @NotNull Condition<? super PsiElement> condition) {
    PsiElement answer = PsiTreeUtil.findFirstParent(element, false, condition);

    do {
      PsiElement next = PsiTreeUtil.findFirstParent(answer, true, condition);
      if (next == null) break;
      answer = next;
    }
    while (true);

    return answer;
  }

  @Nullable
  public static PsiElement getCommonParentOfTypes(@NotNull PsiElement element1,
                                                  @NotNull PsiElement element2,
                                                  @NotNull TokenSet tokenSet) {
    final PsiElement base = PsiTreeUtil.findCommonParent(element1, element2);
    return PsiTreeUtil.findFirstParent(base, false, element -> {
      final ASTNode node = element.getNode();
      return node != null && tokenSet.contains(node.getElementType());
    });
  }
}
