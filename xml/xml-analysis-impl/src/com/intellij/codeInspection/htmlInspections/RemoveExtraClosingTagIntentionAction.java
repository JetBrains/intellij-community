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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
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
  public String getName() {
    return XmlErrorMessages.message("remove.extra.closing.tag.quickfix");
  }


  @Override
  @NotNull
  public String getText() {
    return getName();
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement psiElement = file.findElementAt(offset);
    if (psiElement == null || !psiElement.isValid() || !(psiElement instanceof XmlToken)) {
      return;
    }

    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    doFix(psiElement);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private static void doFix(@NotNull final PsiElement element) throws IncorrectOperationException {
    final XmlToken endNameToken = (XmlToken)element;
    final PsiElement tagElement = endNameToken.getParent();
    if (!(tagElement instanceof XmlTag) && !(tagElement instanceof PsiErrorElement)) return;

    if (tagElement instanceof PsiErrorElement) {
      tagElement.delete();
    }
    else {
      final ASTNode astNode = tagElement.getNode();
      if (astNode != null) {
        final ASTNode endTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(astNode);
        if (endTagStart != null) {
          final Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(tagElement.getContainingFile());
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
    if (!element.isValid() || !(element instanceof XmlToken)) return;
    if (!FileModificationService.getInstance().prepareFileForWrite(element.getContainingFile())) return;

    new WriteCommandAction(project) {
      @Override
      protected void run(@NotNull final Result result) throws Throwable {
        doFix(element);
      }
    }.execute();
  }
}
