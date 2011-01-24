package com.jetbrains.python.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;

import java.util.Collection;

/**
 * @author yole
 */
public class PyInheritorRenameFactory implements AutomaticRenamerFactory {
  @Override
  public boolean isApplicable(PsiElement element) {
    return element instanceof PyClass;
  }

  @Override
  public String getOptionName() {
    return "Rename inheritors";
  }

  @Override
  public boolean isEnabled() {
    return PyCodeInsightSettings.getInstance().RENAME_CLASS_INHERITORS;
  }

  @Override
  public void setEnabled(boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_CLASS_INHERITORS = enabled;
  }

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
      return "Rename Inheritors";
    }

    @Override
    public String getDialogDescription() {
      return "Rename inheritor classes with the following names to:";
    }

    @Override
    public String entityName() {
      return "Inheritor Class";
    }
  }
}
