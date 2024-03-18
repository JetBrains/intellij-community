// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to convert docstrings to the common form according to PEP-257
 * For consistency, always use """triple double quotes""" around docstrings.
 */
public class ConvertDocstringQuickFix extends PsiUpdateModCommandQuickFix {
  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.convert.single.quoted.docstring");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (element instanceof PyStringLiteralExpression && element.isWritable()) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

      String stringText = element.getText();
      int prefixLength = PyStringLiteralUtil.getPrefixLength(stringText);
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
      element.replace(newString);
    }
  }

}
