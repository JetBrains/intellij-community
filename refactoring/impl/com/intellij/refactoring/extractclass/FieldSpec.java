package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiVariable;

class FieldSpec{
    private final boolean setterRequired;
    private final boolean getterRequired;
    private final PsiField field;

    FieldSpec(PsiField field, boolean getterRequired, boolean setterRequired) {
        super();
        this.field = field;
        this.getterRequired = getterRequired;
        this.setterRequired = setterRequired;
    }

    public PsiVariable getField() {
        return field;
    }

    public boolean isSetterRequired() {
        return setterRequired;
    }

    public boolean isGetterRequired() {
        return getterRequired;
    }
}
