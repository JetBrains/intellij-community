// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


public class PyInheritorRenameFactory implements AutomaticRenamerFactory {
  @Override
  public boolean isApplicable(@NotNull PsiElement element) {
    return element instanceof PyClass;
  }

  @Override
  public String getOptionName() {
    return PyBundle.message("refactoring.rename.inheritors");
  }

  @Override
  public boolean isEnabled() {
    return PyCodeInsightSettings.getInstance().RENAME_CLASS_INHERITORS;
  }

  @Override
  public void setEnabled(boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_CLASS_INHERITORS = enabled;
  }

  @NotNull
  @Override
  public AutomaticRenamer createRenamer(PsiElement element, String newName, Collection<UsageInfo> usages) {
    return new PyInheritorRenamer((PyClass) element, newName);
  }

  public static class PyInheritorRenamer extends AutomaticRenamer {
    public PyInheritorRenamer(PyClass element, String newName) {
      myElements.addAll(PyClassInheritorsSearch.search(element, false).findAll());
      suggestAllNames(element.getName(), newName);
    }

    @Override
    public String getDialogTitle() {
      return PyBundle.message("refactoring.rename.inheritors.title");
    }

    @Override
    public String getDialogDescription() {
      return PyBundle.message("refactoring.rename.inheritor.classes.with.the.following.names.to");
    }

    @Override
    public String entityName() {
      return PyBundle.message("refactoring.rename.inheritor.class.entity.name");
    }
  }
}
