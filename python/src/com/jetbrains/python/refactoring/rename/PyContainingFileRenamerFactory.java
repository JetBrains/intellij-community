// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.rename;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.refactoring.rename.naming.NameSuggester;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author yole
 */
public class PyContainingFileRenamerFactory implements AutomaticRenamerFactory {
  @Override
  public boolean isApplicable(@NotNull PsiElement element) {
    if (!(element instanceof PyClass)) {
      return false;
    }
    ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(element, ScopeOwner.class);
    if (scopeOwner instanceof PyFile) {
      String className = ((PyClass) element).getName();
      String fileName = FileUtil.getNameWithoutExtension(scopeOwner.getName());
      return fileName.equalsIgnoreCase(className);
    }
    return false;
  }

  @Override
  public String getOptionName() {
    return "Rename containing file";
  }

  @Override
  public boolean isEnabled() {
    return PyCodeInsightSettings.getInstance().RENAME_CLASS_CONTAINING_FILE;
  }

  @Override
  public void setEnabled(boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_CLASS_CONTAINING_FILE = enabled;
  }

  @NotNull
  @Override
  public AutomaticRenamer createRenamer(PsiElement element, String newName, Collection<UsageInfo> usages) {
    return new PyContainingFileRenamer((PyClass) element, newName);
  }

  public static class PyContainingFileRenamer extends AutomaticRenamer {
    private final PyClass myClass;

    public PyContainingFileRenamer(PyClass element, String newName) {
      myClass = element;
      myElements.add(element.getContainingFile());
      suggestAllNames(element.getName(), newName);
    }

    @Override
    public String getDialogTitle() {
      return "Rename Containing File";
    }

    @Override
    public String getDialogDescription() {
      return "Rename containing file with the following name to: ";
    }

    @Override
    public String entityName() {
      return "Containing File";
    }

    @Override
    protected String nameToCanonicalName(@NonNls String name, PsiNamedElement element) {
      return FileUtil.getNameWithoutExtension(name);
    }

    @Override
    protected String canonicalNameToName(@NonNls String canonicalName, PsiNamedElement element) {
      return canonicalName + "." + FileUtilRt.getExtension(myClass.getContainingFile().getName());
    }

    @Override
    public boolean isSelectedByDefault() {
      return true;
    }

    @Override
    protected String suggestNameForElement(PsiNamedElement element, NameSuggester suggester, String newClassName, String oldClassName) {
      if (element instanceof PyFile && element.getName().equals(oldClassName.toLowerCase() + ".py")) {
        return newClassName.toLowerCase() + ".py";
      }
      return super.suggestNameForElement(element, suggester, newClassName, oldClassName);
    }
  }
}
