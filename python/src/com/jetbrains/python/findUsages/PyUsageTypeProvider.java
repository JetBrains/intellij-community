package com.jetbrains.python.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageTypeProvider;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportStatement;

/**
 * @author yole
 */
public class PyUsageTypeProvider implements UsageTypeProvider {
  private static final UsageType IN_IMPORT = new UsageType("Usage in import statement");

  public UsageType getUsageType(PsiElement element) {
    if (element instanceof PyElement) {
      if (PsiTreeUtil.getParentOfType(element, PyImportStatement.class, PyFromImportStatement.class) != null) {
        return IN_IMPORT;
      }
    }
    return null;
  }
}
