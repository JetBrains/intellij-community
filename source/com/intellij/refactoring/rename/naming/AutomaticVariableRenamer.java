package com.intellij.refactoring.rename.naming;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.usageView.UsageInfo;

import java.util.Iterator;
import java.util.List;

/**
 * @author dsl
 */
public class AutomaticVariableRenamer extends AutomaticRenamer {
  public AutomaticVariableRenamer(PsiClass aClass, String newClassName, List<UsageInfo> usages) {
    for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
      final UsageInfo info = iterator.next();
      if (info.getElement() instanceof PsiJavaCodeReferenceElement
          && info.getElement().getParent() instanceof PsiTypeElement
        &&  info.getElement().getParent().getParent() instanceof PsiVariable
      ) {
        myElements.add(((PsiVariable)info.getElement().getParent().getParent()));
      }
    }
    suggestAllNames(aClass.getName(), newClassName);
  }

  public String getDialogTitle() {
    return "Rename Variables";
  }

  public String getDialogDescription() {
    return "Rename variables with the following names to:";
  }

  public String entityName() {
    return "Variable";
  }

  public String nameToCanonicalName(String name, PsiNamedElement psiVariable) {
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(psiVariable.getManager());
    return codeStyleManager.variableNameToPropertyName(name, codeStyleManager.getVariableKind((PsiVariable)psiVariable));
  }

  public String canonicalNameToName(String canonicalName, PsiNamedElement psiVariable) {
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(psiVariable.getManager());
    return codeStyleManager.propertyNameToVariableName(canonicalName, codeStyleManager.getVariableKind((PsiVariable)psiVariable));
  }

}