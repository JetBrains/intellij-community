package com.intellij.refactoring.rename.naming;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public class ConstructorParameterOnFieldRenameRenamer extends AutomaticRenamer {
  @NonNls
  protected String canonicalNameToName(@NonNls final String canonicalName, final PsiNamedElement element) {
    return element.getManager().getCodeStyleManager().propertyNameToVariableName(canonicalName, VariableKind.PARAMETER);
  }

  protected String nameToCanonicalName(@NonNls final String name, final PsiNamedElement element) {
    return element.getManager().getCodeStyleManager().variableNameToPropertyName(name, VariableKind.FIELD);
  }

  public ConstructorParameterOnFieldRenameRenamer(PsiField aField, String newFieldName) {
    final CodeStyleManager styleManager = aField.getManager().getCodeStyleManager();
    String propertyName = styleManager.variableNameToPropertyName(aField.getName(), VariableKind.FIELD);
    final String paramName = styleManager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
    final PsiClass aClass = aField.getContainingClass();
    for (final PsiMethod constructor : aClass.getConstructors()) {
      final PsiParameter[] parameters = constructor.getParameterList().getParameters();
      for (final PsiParameter parameter : parameters) {
        if (paramName.equals(parameter.getName())) {
          myElements.add(parameter);
        }
      }
    }

    suggestAllNames(aField.getName(), newFieldName);
  }

  public String getDialogTitle() {
    return RefactoringBundle.message("rename.constructor.parameters.title");
  }

  public String getDialogDescription() {
    return RefactoringBundle.message("rename.constructor.parameters.with.the.following.names.to");
  }

  public String entityName() {
    return RefactoringBundle.message("entity.name.constructor.parameter");
  }
}