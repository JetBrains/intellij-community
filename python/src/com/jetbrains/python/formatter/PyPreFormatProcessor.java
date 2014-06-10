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
package com.jetbrains.python.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyPreFormatProcessor implements PreFormatProcessor {
  @NotNull
  @Override
  public TextRange process(@NotNull ASTNode element, @NotNull TextRange range) {
    PsiElement psiElement = element.getPsi();
    if (psiElement == null) return range;

    if (!psiElement.getLanguage().is(PythonLanguage.getInstance())) return range;

    PsiFile file = psiElement.isValid() ? psiElement.getContainingFile() : null;
    if (file == null) return range;

    Project project = psiElement.getProject();

    return new PyCommentFormatter(project).process(psiElement, range);
  }

  /**
   * @author traff
   */
  public static class PyCommentFormatter extends PyRecursiveElementVisitor {
    private final Project myProject;
    private final CodeStyleSettings mySettings;
    private final PyCodeStyleSettings myPyCodeStyleSettings;
    private TextRange myRange;
    private int myDelta = 0;

    public PyCommentFormatter(Project project) {
      myProject = project;
      mySettings = CodeStyleSettingsManager.getSettings(project);
      myPyCodeStyleSettings = mySettings.getCustomSettings(PyCodeStyleSettings.class);
    }

    public TextRange process(PsiElement element, TextRange range) {
      if (!myPyCodeStyleSettings.SPACE_AFTER_NUMBER_SIGN) {
        return range;
      }
      myRange = range;
      element.accept(this);
      return TextRange.create(range.getStartOffset(), range.getEndOffset() + myDelta);
    }

    @Override
    public void visitComment(PsiComment element) {
      if (!myRange.contains(element.getTextRange())) {
        return;
      }
      String text = element.getText();
      int commentStart = text.indexOf('#');
      if (commentStart != -1 && (commentStart + 1) < text.length()) {
        if (text.charAt(commentStart+1) == '!') {
          return; //shebang
        }
        String commentText = StringUtil.trimLeading(text.substring(commentStart + 1));

        String newText = "# " + commentText;
        if (!newText.equals(text)) {
          myDelta += newText.length() - text.length();
          element.replace(
            PyElementGenerator.getInstance(myProject).createFromText(LanguageLevel.getDefault(), PsiComment.class, newText));
        }
      }
    }
  }
}
