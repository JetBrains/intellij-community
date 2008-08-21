package com.intellij.refactoring.introduceparameterobject;

import com.intellij.psi.PsiParameter;

class ParameterSpec {
    private final boolean setterRequired;
    private final PsiParameter parameter;

    ParameterSpec(PsiParameter parameter, boolean setterRequired) {
        super();
        this.parameter = parameter;
        this.setterRequired = setterRequired;
    }

    public PsiParameter getParameter() {
        return parameter;
    }

    public boolean isSetterRequired() {
        return setterRequired;
    }
}
