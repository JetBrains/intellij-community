package com.jetbrains.python.packaging.setupPy;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyKeywordArgumentProvider;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class SetupKeywordArgumentProvider implements PyKeywordArgumentProvider {
  @Override
  public List<String> getKeywordArguments(PyFunction function) {
    if ("setup".equals(function.getName())) {
      final ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(function, ScopeOwner.class, true);
      if (scopeOwner instanceof PyFile) {
        final PyFile file = (PyFile)scopeOwner;
        if (file.getName().equals("core.py") && file.getParent().getName().equals("distutils")) {
          final List<String> arguments = getSetupPyKeywordArguments(file);
          if (arguments != null) {
            return arguments;
          }
        }
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  private static List<String> getSetupPyKeywordArguments(PyFile file) {
    final PyTargetExpression keywords = file.findTopLevelAttribute("setup_keywords");
    if (keywords != null) {
      return PyUtil.strListValue(keywords.findAssignedValue());
    }
    return null;
  }
}
