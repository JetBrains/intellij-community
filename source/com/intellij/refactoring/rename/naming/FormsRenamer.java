package com.intellij.refactoring.rename.naming;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;

/**
 * @author ven
 */
public class FormsRenamer extends AutomaticRenamer {
  public String nameToCanonicalName(String name, PsiNamedElement psiFile) {
    if (name.endsWith(".form")) return name.substring(0, name.length() - ".form".length());
    return name;
  }

  public String canonicalNameToName(String canonicalName, PsiNamedElement psiFile) {
    return canonicalName.indexOf(".") < 0 ? canonicalName + ".form" : canonicalName;
  }

  public FormsRenamer(PsiClass aClass, String newClassName) {
    if (aClass.getQualifiedName() != null) {
      PsiFile[] forms = aClass.getManager().getSearchHelper().findFormsBoundToClass(aClass.getQualifiedName());
      for (int i = 0; i < forms.length; i++) {
        final PsiFile form = forms[i];
        if (form.getName() != null) {
          myElements.add(form);
        }
      }

      suggestAllNames(aClass.getName(), newClassName);
    }
  }

  public String getDialogTitle() {
    return "Rename bound forms";
  }

  public String getDialogDescription() {
    return "Rename forms with the following names to:";
  }

  public String entityName() {
    return "Form";
  }
}
