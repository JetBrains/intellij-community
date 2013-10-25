/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jun 29, 2006
 * Time: 6:09:35 PM
 */
package com.intellij.xml.util;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim Mossienko
 */
public class CheckValidXmlInScriptBodyInspection extends CheckValidXmlInScriptBodyInspectionBase {

  @Override
  protected InsertQuotedCharacterQuickFix createFix(PsiFile psiFile,
                                                    PsiElement psiElement,
                                                    int offsetInElement) {
    return new InsertQuotedCharacterQuickFix(
      psiFile,
      psiElement,
      offsetInElement
    );
  }

  private static class InsertQuotedCharacterQuickFix implements LocalQuickFix {
    private final PsiFile psiFile;
    private final PsiElement psiElement;
    private final int startInElement;

    public InsertQuotedCharacterQuickFix(PsiFile psiFile, PsiElement psiElement, int startInElement) {
      this.psiFile = psiFile;
      this.psiElement = psiElement;
      this.startInElement = startInElement;
    }

    @Override
    @NotNull
    public String getName() {
      final String character = getXmlCharacter();

      return XmlBundle.message(
        "unescaped.xml.character.fix.message",
        character.equals("&") ?
          XmlBundle.message("unescaped.xml.character.fix.message.parameter"):
          character
      );
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {
      if (!FileModificationService.getInstance().prepareFileForWrite(psiFile)) return;
      final TextRange range = psiElement.getTextRange();
      OpenFileDescriptor descriptor = new OpenFileDescriptor(
        project,
        psiFile.getVirtualFile(),
        range.getStartOffset() + startInElement
      );

      final Editor editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
      if (editor == null) return;

      final String xmlCharacter = getXmlCharacter();
      String replacement = xmlCharacter.equals("&") ? AMP_ENTITY_REFERENCE : LT_ENTITY_REFERENCE;
      replacement = psiElement.getText().replace(xmlCharacter,replacement);

      editor.getDocument().replaceString(
        range.getStartOffset(),
        range.getEndOffset(),
        replacement
      );
    }

    private String getXmlCharacter() {
      return psiElement.getText().substring(startInElement, startInElement + 1);
    }
  }
}
