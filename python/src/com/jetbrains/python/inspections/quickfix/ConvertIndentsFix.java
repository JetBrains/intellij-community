/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.actions.ConvertIndentsActionBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class ConvertIndentsFix implements LocalQuickFix {
  private final boolean myToSpaces;

  public ConvertIndentsFix(boolean toSpaces) {
    myToSpaces = toSpaces;
  }

  @NotNull
  @Override
  public String getName() {
    return myToSpaces ? "Convert indents to spaces" : "Convert indents to tabs";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Convert indents";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiFile file = descriptor.getPsiElement().getContainingFile();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document != null) {
      int tabSize = CodeStyle.getIndentSize(file);
      TextRange allDoc = new TextRange(0, document.getTextLength());
      if (myToSpaces) {
        ConvertIndentsActionBase.convertIndentsToSpaces(document, tabSize, allDoc);
      }
      else {
        ConvertIndentsActionBase.convertIndentsToTabs(document, tabSize, allDoc);
      }
    }
  }
}
