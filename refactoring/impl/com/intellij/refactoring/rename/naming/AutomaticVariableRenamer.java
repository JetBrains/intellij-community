package com.intellij.refactoring.rename.naming;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.HashSet;

import java.util.List;
import java.util.Set;

/**
 * @author dsl
 */
public class AutomaticVariableRenamer extends AutomaticRenamer {
  private final Set<PsiNamedElement> myToUnpluralize = new HashSet<PsiNamedElement>();

  public AutomaticVariableRenamer(PsiClass aClass, String newClassName, List<UsageInfo> usages) {
    for (final UsageInfo info : usages) {
      final PsiElement element = info.getElement();
      if (!(element instanceof PsiJavaCodeReferenceElement)) continue;
      final PsiVariable variable = PsiTreeUtil.getParentOfType(element, PsiVariable.class);
      if (variable == null) continue;
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement == null) continue;
      final PsiJavaCodeReferenceElement ref = typeElement.getInnermostComponentReferenceElement();
      if (ref == null) continue;
      if (ref.equals(element)) {
        myElements.add(variable);
        if (variable.getType() instanceof PsiArrayType) {
          myToUnpluralize.add(variable);
        }
      }
      else {
        PsiType collectionType =
          variable.getManager().getElementFactory().createTypeByFQClassName("java.util.Collection", variable.getResolveScope());
        if (!collectionType.isAssignableFrom(variable.getType())) continue;
        final PsiTypeElement[] typeParameterElements = ref.getParameterList().getTypeParameterElements();
        for (PsiTypeElement typeParameterElement : typeParameterElements) {
          final PsiJavaCodeReferenceElement parameterRef = typeParameterElement.getInnermostComponentReferenceElement();
          if (parameterRef != null && parameterRef.equals(element)) {
            myElements.add(variable);
            myToUnpluralize.add(variable);
            break;
          }
        }
      }
    }
    suggestAllNames(aClass.getName(), newClassName);
  }

  public String getDialogTitle() {
    return RefactoringBundle.message("rename.variables.title");
  }

  public String getDialogDescription() {
    return RefactoringBundle.message("rename.variables.with.the.following.names.to");
  }

  public String entityName() {
    return RefactoringBundle.message("entity.name.variable");
  }

  public String nameToCanonicalName(String name, PsiNamedElement psiVariable) {
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(psiVariable.getManager());
    final String propertyName = codeStyleManager.variableNameToPropertyName(name, codeStyleManager.getVariableKind((PsiVariable)psiVariable));
    if (myToUnpluralize.contains(psiVariable)) {
      final String singular = StringUtil.unpluralize(propertyName);
      if (singular != null) return singular;
      myToUnpluralize.remove(psiVariable); // no need to pluralize since it was initially in singular form
    }
    return propertyName;
  }

  public String canonicalNameToName(String canonicalName, PsiNamedElement psiVariable) {
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(psiVariable.getManager());
    final String variableName =
      codeStyleManager.propertyNameToVariableName(canonicalName, codeStyleManager.getVariableKind((PsiVariable)psiVariable));
    if (myToUnpluralize.contains(psiVariable)) return StringUtil.pluralize(variableName);
    return variableName;
  }
}