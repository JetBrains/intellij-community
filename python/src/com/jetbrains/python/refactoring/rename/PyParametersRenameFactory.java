// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import java.util.Collection;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;


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
    return PyBundle.message("refactoring.rename.parameters.in.hierarchy");
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
          if (named != null && Objects.equals(named.getName(), element.getName())) {
            myElements.add(named);
          }
        }
        return true;
      });
      suggestAllNames(element.getName(), newName);
    }

    @Override
    public String getDialogTitle() {
      return PyBundle.message("refactoring.rename.parameters.title");
    }

    @Override
    public String getDialogDescription() {
      return PyBundle.message("refactoring.rename.parameter.in.hierarchy.to");
    }

    @Override
    public String entityName() {
      return PyBundle.message("refactoring.rename.parameter.entity.name");
    }

    @Override
    public boolean isSelectedByDefault() {
      return true;
    }
  }
}
