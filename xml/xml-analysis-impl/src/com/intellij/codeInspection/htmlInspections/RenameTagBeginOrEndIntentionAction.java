// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RenameTagBeginOrEndIntentionAction implements IntentionAction {
  private final boolean myStart;
  private final String myTargetName;
  private final String mySourceName;

  RenameTagBeginOrEndIntentionAction(final @NotNull String targetName, final @NotNull String sourceName, final boolean start) {
    myTargetName = targetName;
    mySourceName = sourceName;
    myStart = start;
  }

  @Override
  public @NotNull String getFamilyName() {
    return getName();
  }

  @Override
  public @NotNull String getText() {
    return getName();
  }

  @Override
  public boolean isAvailable(final @NotNull Project project, final Editor editor, final PsiFile psiFile) {
    return true;
  }

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile psiFile) throws IncorrectOperationException {
    final int offset = editor.getCaretModel().getOffset();
    PsiElement psiElement = psiFile.findElementAt(offset);

    if (psiElement == null) return;

    if (psiElement instanceof PsiWhiteSpace) psiElement = PsiTreeUtil.prevLeaf(psiElement);
    if (psiElement instanceof XmlToken) {
      final IElementType tokenType = ((XmlToken)psiElement).getTokenType();
      if (tokenType != XmlTokenType.XML_NAME) {
        if (tokenType == XmlTokenType.XML_TAG_END) {
          psiElement = psiElement.getPrevSibling();
          if (psiElement == null) return;
        }
      }

      PsiElement target;
      final String text = psiElement.getText();
      if (!myTargetName.equals(text)) {
        target = psiElement;
      }
      else {
        // we're in the other
        target = findOtherSide(psiElement, myStart);
      }

      if (target != null) {
        final Document document = psiFile.getViewProvider().getDocument();
        if (document != null) {
          final TextRange textRange = target.getTextRange();
          document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), myTargetName);
        }
      }

    }
  }

  public static @Nullable PsiElement findOtherSide(PsiElement psiElement, final boolean start) {
    PsiElement target = null;
    PsiElement parent = psiElement.getParent();
    if (parent instanceof PsiErrorElement) {
      parent = parent.getParent();
    }

    if (parent instanceof XmlTag) {
      if (start) {
        target = XmlTagUtil.getStartTagNameElement((XmlTag)parent);
      }
      else {
        target = XmlTagUtil.getEndTagNameElement((XmlTag)parent);
        if (target == null) {
          final PsiErrorElement errorElement = PsiTreeUtil.getChildOfType(parent, PsiErrorElement.class);
          target = XmlWrongClosingTagNameInspection.findEndTagName(errorElement);
        }
      }
    }
    return target;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  public @NotNull @IntentionName String getName() {
    return myStart
           ? XmlAnalysisBundle.message("xml.intention.rename.start.tag", mySourceName, myTargetName)
           : XmlAnalysisBundle.message("xml.intention.rename.end.tag", mySourceName, myTargetName);
  }
}
