/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.xml.util;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class CollapseTagIntention implements LocalQuickFix, IntentionAction {

  @Override
  @NotNull
  public String getName() {
    return XmlBundle.message("xml.inspections.replace.tag.empty.body.with.empty.end");
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return getName();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    applyFix(project, descriptor.getPsiElement());
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    XmlTag tag = getTag(editor, file);
    return tag != null && !tag.isEmpty() && tag.getSubTags().length == 0 && tag.getValue().getTrimmedText().isEmpty() &&
           CheckTagEmptyBodyInspection.isCollapsibleTag(tag);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    applyFix(project, getTag(editor, file));
  }

  private static XmlTag getTag(Editor editor, PsiFile file) {
    FileViewProvider provider = file.getViewProvider();
    PsiElement element = provider.findElementAt(editor.getCaretModel().getOffset(), provider.getBaseLanguage());
    return PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static void applyFix(@NotNull final Project project, final PsiElement tag) {
    if (!FileModificationService.getInstance().prepareFileForWrite(tag.getContainingFile())) {
      return;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final ASTNode child = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());
    if (child == null) return;
    final int offset = child.getTextRange().getStartOffset();
    VirtualFile file = tag.getContainingFile().getVirtualFile();
    final Document document = FileDocumentManager.getInstance().getDocument(file);

    new WriteCommandAction(project) {
      @Override
      protected void run(@NotNull final Result result) throws Throwable {
        assert document != null;
        document.replaceString(offset, tag.getTextRange().getEndOffset(), "/>");
        PsiDocumentManager.getInstance(project).commitDocument(document);
        CodeStyleManager.getInstance(project).reformat(tag);
      }
    }.execute();

  }
}
