package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.ConstantExpressionEvaluator;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 10.08.2005
 * Time: 18:25:51
 * To change this template use File | Settings | File Templates.
 */
public class ValueDocTagInfo implements JavadocTagInfo {
  public String getName() {
    return "value";
  }

  public boolean isInline() {
    return true;
  }

  public boolean isValidInContext(PsiElement element) {
    return true;
  }

  public Object[] getPossibleValues(PsiElement context, PsiElement place, String prefix) {
    return null;
  }

  public String checkTagValue(PsiDocTagValue value) {
    boolean hasReference = (value != null && value.getFirstChild() != null);
    if (hasReference) {
      if (!value.getManager().getEffectiveLanguageLevel().hasEnumKeywordAndAutoboxing()) {
        return "@value tag may not have any arguments when JDK 1.4 or earlier is used";
      }
    }

    if (value != null) {
      PsiReference reference = value.getReference();
      if (reference != null) {
        PsiElement target = reference.resolve();
        if (target != null) {
          if (!(target instanceof PsiField)) {
            return "@value tag must reference a field";
          }
          PsiField field = (PsiField) target;
          if (!field.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
            return "@value tag must reference a static field";
          }
          if (field.getInitializer() == null ||
              ConstantExpressionEvaluator.computeConstantExpression(field.getInitializer(), null, false) == null) {
            return "@value tag must reference a field with a constant initializer";
          }
        }
      }
    }

    return null;
  }

  public PsiReference getReference(PsiDocTagValue value) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
