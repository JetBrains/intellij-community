/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 21, 2001
 * Time: 4:35:07 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;

public class RefParameter extends RefElement {
  private static final int USED_FOR_READING_MASK = 0x10000;
  private static final int USED_FOR_WRITING_MASK = 0x20000;
  private static final String VALUE_UNDEFINED = "#";

  private final short myIndex;
  private String myActualValueTemplate;

  public RefParameter(PsiParameter parameter, int index, RefManager manager) {
    super(parameter, manager);

    myIndex = (short)index;
    myActualValueTemplate = VALUE_UNDEFINED;
  }

  public void parameterReferenced(boolean forWriting) {
    if (forWriting) {
      setUsedForWriting();
    } else {
      setUsedForReading();
    }
  }

  public boolean isUsedForReading() {
    return checkFlag(USED_FOR_READING_MASK);
  }

  private void setUsedForReading() {
    setFlag(true, USED_FOR_READING_MASK);
  }

  public boolean isUsedForWriting() {
    return checkFlag(USED_FOR_WRITING_MASK);
  }

  private void setUsedForWriting() {
    setFlag(true, USED_FOR_WRITING_MASK);
  }

  public void accept(RefVisitor visitor) {
    visitor.visitParameter(this);
  }

  public int getIndex() {
    return myIndex;
  }

  protected void initialize(PsiElement elem) {
    // Empty is important here. Final modifier is externally set by RefMethod.buildReferences.
  }

  public void updateTemplateValue(PsiExpression expression) {
    if (myActualValueTemplate == null) return;

    String newTemplate = null;
    if (expression instanceof PsiLiteralExpression) {
      PsiLiteralExpression psiLiteralExpression = (PsiLiteralExpression) expression;
      newTemplate = psiLiteralExpression.getText();
    } else if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
      PsiElement resolved = referenceExpression.resolve();
      if (resolved instanceof PsiField) {
        PsiField psiField = (PsiField) resolved;
        if (psiField.hasModifierProperty(PsiModifier.STATIC) &&
            psiField.hasModifierProperty(PsiModifier.FINAL)) {
          newTemplate = PsiFormatUtil.formatVariable(psiField, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_FQ_NAME, PsiSubstitutor.EMPTY);
        }
      }
    }

    if (myActualValueTemplate == VALUE_UNDEFINED) {
      myActualValueTemplate = newTemplate;
    } else if (!Comparing.equal(myActualValueTemplate, newTemplate)) {
      myActualValueTemplate = null;
    }
  }

  public String getActualValueIfSame() {
    if (myActualValueTemplate == VALUE_UNDEFINED) return null;
    return myActualValueTemplate;
  }

  public void initializeFinalFlag() {
    setIsFinal(getElement().hasModifierProperty(PsiModifier.FINAL));
  }
}
