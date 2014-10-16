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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to convert docstrings to the common form according to PEP-257
 * For consistency, always use """triple double quotes""" around docstrings.
 */
public class ConvertDocstringQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.convert.single.quoted.docstring");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement expression = descriptor.getPsiElement();
    if (expression instanceof PyStringLiteralExpression && expression.isWritable()) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

      String stringText = expression.getText();
      int prefixLength = PyStringLiteralExpressionImpl
        .getPrefixLength(stringText);
      String prefix = stringText.substring(0, prefixLength);
      String content = stringText.substring(prefixLength);
      if (content.startsWith("'''") ) {
        content = content.substring(3, content.length()-3);
      } else if (content.startsWith("\"\"\""))
        return;
      else {
        content = content.length() == 1 ? "" : content.substring(1, content.length()-1);
      }
      if (content.endsWith("\""))
        content = StringUtil.replaceSubstring(content, TextRange.create(content.length()-1, content.length()), "\\\"");

      PyExpression newString = elementGenerator.createDocstring(prefix+"\"\"\"" + content + "\"\"\"").getExpression();
      expression.replace(newString);
    }
  }

}
