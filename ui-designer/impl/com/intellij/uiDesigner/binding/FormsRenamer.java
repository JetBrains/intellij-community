package com.intellij.uiDesigner.binding;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.uiDesigner.GuiFormFileType;

import java.util.List;

/**
 * @author ven
 */
public class FormsRenamer extends AutomaticRenamer {
  public String nameToCanonicalName(String name, PsiNamedElement psiFile) {
    if (name.endsWith(GuiFormFileType.DOT_DEFAULT_EXTENSION)) return name.substring(0, name.length() - GuiFormFileType.DOT_DEFAULT_EXTENSION.length());
    return name;
  }

  public String canonicalNameToName(String canonicalName, PsiNamedElement psiFile) {
    return canonicalName.contains(".") ? canonicalName : canonicalName + GuiFormFileType.DOT_DEFAULT_EXTENSION;
  }

  public FormsRenamer(PsiClass aClass, String newClassName) {
    if (aClass.getQualifiedName() != null) {
      List<PsiFile> forms = FormClassIndex.findFormsBoundToClass(aClass);
      myElements.addAll(forms);
      suggestAllNames(aClass.getName(), newClassName);
    }
  }

  @Override
  public boolean isSelectedByDefault() {
    return true;
  }

  public String getDialogTitle() {
    return RefactoringBundle.message("rename.bound.forms.title");
  }

  public String getDialogDescription() {
    return RefactoringBundle.message("rename.forms.with.the.following.names.to");
  }

  public String entityName() {
    return RefactoringBundle.message("entity.name.form");
  }
}
