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
package com.jetbrains.python.codeInsight.liveTemplates;

import com.intellij.codeInsight.template.FileTypeBasedContextType;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author yole
 */
public class PythonTemplateContextType extends FileTypeBasedContextType {
  public PythonTemplateContextType() {
    super("Python", "Python", PythonFileType.INSTANCE);
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    if (super.isInContext(file, offset)) {
      final PsiElement element = file.findElementAt(offset);
      if (element != null) {
        return !(isAfterDot(element) || element instanceof PsiComment || element instanceof PyStringLiteralExpression ||
                 isInsideParameterList(element));
      }
    }
    return false;
  }

  private static boolean isInsideParameterList(@NotNull PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PyParameterList.class) != null;
  }

  private static boolean isAfterDot(@NotNull PsiElement element) {
    final PsiElementPattern.Capture<PsiElement> capture = psiElement().afterLeafSkipping(psiElement().whitespace(),
                                                                                         psiElement().withElementType(PyTokenTypes.DOT));
    return capture.accepts(element, new ProcessingContext());
  }
}
