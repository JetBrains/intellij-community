package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

/**
 * @author ven
 */
public class CreateGetterOrSetterAction implements IntentionAction{
  private boolean myToCreateGetter;
  private PsiField myField;
  private String myPropertyName;

  public CreateGetterOrSetterAction(boolean toCreateGetter, PsiField field) {
    myToCreateGetter = toCreateGetter;
    myField = field;
    Project project = field.getProject();
    myPropertyName = PropertyUtil.suggestPropertyName(project, field);
  }

  public String getText() {
    return MessageFormat.format("Create " + (myToCreateGetter ? "getter" : "setter") + " for ''{0}''",
                                             new Object[]{myField.getName()});
  }

  public String getFamilyName() {
    return "Create Accessor for Unused Field";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!myField.isValid()) return false;
    PsiClass aClass = myField.getContainingClass();
    if (aClass != null) {
      if (myToCreateGetter) {
        return PropertyUtil.findPropertyGetter(aClass, myPropertyName, myField.hasModifierProperty(PsiModifier.STATIC), false) == null;
      } else {
        return PropertyUtil.findPropertySetter(aClass, myPropertyName, myField.hasModifierProperty(PsiModifier.STATIC), false) == null;
      }
    }

    return false;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiClass aClass = myField.getContainingClass();
    if (myToCreateGetter) {
        aClass.add(PropertyUtil.generateGetterPrototype(myField));
      } else {
        aClass.add(PropertyUtil.generateSetterPrototype(myField));
      }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
