// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class ReturnValueBeanBuilder {
  private final List<PsiTypeParameter> myTypeParams = new ArrayList<>();
  private String myClassName;
  private String myPackageName;
  private Project myProject;
  private PsiFile myFile;
  private PsiType myValueType;
  private boolean myStatic;

  public void setClassName(String className) {
    myClassName = className;
  }

  public void setPackageName(String packageName) {
    myPackageName = packageName;
  }

  public void setTypeArguments(List<? extends PsiTypeParameter> typeParams) {
    myTypeParams.clear();
    myTypeParams.addAll(typeParams);
  }

  public void setProject(Project project) {
    myProject = project;
  }

  public void setFile(@NotNull PsiFile file) {
    myFile = file;
  }

  public void setValueType(PsiType valueType) {
    myValueType = valueType;
  }

  public void setStatic(boolean isStatic) {
    myStatic = isStatic;
  }

  public String buildBeanClass() throws IOException {
    final @NonNls StringBuilder out = new StringBuilder(1024);

    if (!myPackageName.isEmpty()) {
      out.append("package ").append(myPackageName).append(";\n\n");
    }

    out.append("public ");
    if (myStatic) out.append("static ");
    out.append("class ").append(myClassName);
    if (!myTypeParams.isEmpty()) {
      out.append('<');
      boolean first = true;
      for (PsiTypeParameter typeParam : myTypeParams) {
        if (!first) {
          out.append(',');
        }
        final String parameterText = typeParam.getText();
        out.append(parameterText);
        first = false;
      }
      out.append('>');
    }

    out.append(" {\n");
    outputField(out);
    out.append("\n\n");
    outputConstructor(out);
    out.append("\n\n");
    outputGetter(out);
    out.append("\n}\n");

    return out.toString();
  }

  private void outputField(@NonNls StringBuilder out) {
    final String typeText = myValueType.getCanonicalText(false);
    out.append('\t' + "private final ").append(typeText).append(' ').append(getFieldName("value")).append(";");
  }

  private void outputConstructor(@NonNls StringBuilder out) {
    final String typeText = myValueType.getCanonicalText(true);
    final String name = "value";
    final String parameterName = JavaCodeStyleManager.getInstance(myProject).propertyNameToVariableName(name, VariableKind.PARAMETER);
    final String fieldName = getFieldName(name);
    out.append("\tpublic ").append(myClassName).append('(');
    out.append(
      getSettings().getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS ?
      "final " : "");
    out.append(typeText).append(' ').append(parameterName);
    out.append(") {\n");
    if (fieldName.equals(parameterName)) {
      out.append("\t\tthis.").append(fieldName).append(" = ").append(parameterName).append(";\n");
    }
    else {
      out.append("\t\t").append(fieldName).append(" = ").append(parameterName).append(";\n");
    }
    out.append("\t}");
  }

  private CodeStyleSettings getSettings() {
    return myFile != null ? CodeStyle.getSettings(myFile) : CodeStyle.getProjectOrDefaultSettings(myProject);
  }

  private void outputGetter(@NonNls StringBuilder out) {
    final String typeText = myValueType.getCanonicalText(true);
    final String name = "value";
    final String capitalizedName = StringUtil.capitalize(name);
    final String fieldName = getFieldName(name);
    out.append("\tpublic ").append(typeText).append(" get").append(capitalizedName).append("() {\n");
    out.append("\t\treturn ").append(fieldName).append(";\n");
    out.append("\t}");
  }

  private String getFieldName(final String name) {
    return JavaCodeStyleManager.getInstance(myProject).propertyNameToVariableName(name, VariableKind.FIELD);
  }
}
