// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.rename;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author yole
 */
public class PyParametersRenameFactory implements AutomaticRenamerFactory {
  @Override
  public boolean isApplicable(@NotNull PsiElement element) {
    if (element instanceof PyParameter) {
      PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
      return function != null && function.getContainingClass() != null;
    }
    return false;
  }

  @Override
  public String getOptionName() {
    return "Rename parameters in hierarchy";
  }

  @Override
  public boolean isEnabled() {
    return PyCodeInsightSettings.getInstance().RENAME_PARAMETERS_IN_HIERARCHY;
  }

  @Override
  public void setEnabled(boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_PARAMETERS_IN_HIERARCHY = enabled;
  }

  @NotNull
  @Override
  public AutomaticRenamer createRenamer(PsiElement element, String newName, Collection<UsageInfo> usages) {
    return new PyParametersRenamer((PyParameter)element, newName);
  }

  public static class PyParametersRenamer extends AutomaticRenamer {

    public PyParametersRenamer(final PyParameter element, String newName) {
      PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
      PyOverridingMethodsSearch.search(function, true).forEach(pyFunction -> {
        PyParameter[] parameters = pyFunction.getParameterList().getParameters();
        for (PyParameter parameter : parameters) {
          PyNamedParameter named = parameter.getAsNamed();
          if (named != null && Comparing.equal(named.getName(), element.getName())) {
            myElements.add(named);
          }
        }
        return true;
      });
      suggestAllNames(element.getName(), newName);
    }

    @Override
    public String getDialogTitle() {
      return "Rename Parameters";
    }

    @Override
    public String getDialogDescription() {
      return "Rename parameter in hierarchy to:";
    }

    @Override
    public String entityName() {
      return "Parameter";
    }

    @Override
    public boolean isSelectedByDefault() {
      return true;
    }
  }
}
