/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author spleaner
 */
public class RemoveExtraClosingTagIntentionAction implements LocalQuickFix, IntentionAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return XmlErrorMessages.message("remove.extra.closing.tag.quickfix");
  }


  @Override
  @NotNull
  public String getText() {
    return getName();
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    PsiElement psiElement = file.findElementAt(editor.getCaretModel().getOffset());
    return psiElement instanceof XmlToken && 
           (psiElement.getParent() instanceof XmlTag || psiElement.getParent() instanceof PsiErrorElement);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    doFix(Objects.requireNonNull(file.findElementAt(editor.getCaretModel().getOffset())).getParent());
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private static void doFix(@NotNull PsiElement tagElement) throws IncorrectOperationException {
    if (tagElement instanceof PsiErrorElement) {
      tagElement.delete();
    }
    else {
      final ASTNode astNode = tagElement.getNode();
      if (astNode != null) {
        final ASTNode endTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(astNode);
        if (endTagStart != null) {
          final Document document = PsiDocumentManager.getInstance(tagElement.getProject()).getDocument(tagElement.getContainingFile());
          if (document != null) {
            document.deleteString(endTagStart.getStartOffset(), tagElement.getLastChild().getTextRange().getEndOffset());
          }
        }
      }
    }
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof XmlToken)) return;

    doFix(element.getParent());
  }
}
