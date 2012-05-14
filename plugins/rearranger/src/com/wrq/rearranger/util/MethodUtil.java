/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.wrq.rearranger.settings.attributeGroups.GetterSetterDefinition;

/** Code to determine if a method is truly a simple getter or setter, courtesy of Alain Ravet. */
public final class MethodUtil {

  public static boolean isGetter(PsiMethod i_method,
                                 GetterSetterDefinition gsd)
  {
    if (i_method == null) {
      return false;
    }

    final boolean hasParameters = 1 <= nofParameters(i_method);
    if (hasParameters) {
      return false;
    }


    final String name = i_method.getName();

    if (name.startsWith("get")) return getterNameMatchedReturnedValue(i_method, name, "get", gsd);
    if (name.startsWith("is")) return getterNameMatchedReturnedValue(i_method, name, "is", gsd);
    if (name.startsWith("has")) return getterNameMatchedReturnedValue(i_method, name, "has", gsd);

    return false;
  }

  private static boolean getterNameMatchedReturnedValue(PsiMethod i_method,
                                                        String i_name,
                                                        String i_prefix,
                                                        GetterSetterDefinition gsd)
  {
    String i_methodNameTrail = i_name.substring(i_prefix.length());
    if (!nameIsWellFormed(i_methodNameTrail)) {
      return false;
    }

    if (isAbstract(i_method)) {
      return false;
    }

    boolean nameOK = false, bodyOK = false;
    switch (gsd.getGetterNameCriterion()) {
      case GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX:
        nameOK = true;
        break;
      case GetterSetterDefinition.GETTER_NAME_MATCHES_FIELD:
        final String nameFromBody = nameOfVariableReturnedInBody(i_method);
        final String propertyName = propertyNameFromMethodTrail(i_methodNameTrail);
        nameOK = nameFromBody.equals(fieldName(propertyName, i_method.getProject()));
        break;
    }
    switch (gsd.getGetterBodyCriterion()) {
      case GetterSetterDefinition.GETTER_BODY_IMMATERIAL:
        bodyOK = true;
        break;
      case GetterSetterDefinition.GETTER_BODY_RETURNS:
        bodyOK = methodContainsReturnStatementOnly(i_method);
        break;
      case GetterSetterDefinition.GETTER_BODY_RETURNS_FIELD:
        final String nameFromBody = nameOfVariableReturnedInBody(i_method);
        final String propertyName = propertyNameFromMethodTrail(i_methodNameTrail);
        bodyOK = nameFromBody.equals(fieldName(propertyName, i_method.getProject()));
        break;
    }
    return nameOK && bodyOK;
  }

  private static boolean methodContainsReturnStatementOnly(PsiMethod i_method) {
    final PsiStatement[] statements = i_method.getBody().getStatements();
    return isReturnStatement(statements[0]);
  }

  private static String nameOfVariableReturnedInBody(PsiMethod i_method) {

    final PsiStatement[] statements = i_method.getBody().getStatements();

    if (nofStatements(i_method) == 0) {
      return "";
    }

    int returnIndex = statements.length - 1;
    if (!isReturnStatement(statements[returnIndex])) {
      return "";
    }
    final PsiReturnStatement returnStatement = (PsiReturnStatement)statements[returnIndex];
    final PsiExpression returnValue = returnStatement.getReturnValue();
    if (null == returnValue) {
      return "";
    }
    String returnValueText = returnValue.getText();
    returnValueText = returnValueText.replaceFirst("this\\.", "");
    return returnValueText;
  }

  private static String propertyNameFromMethodTrail(String i_source) {
    if (i_source.length() > 0) {
      final String left = i_source.substring(0, 1).toLowerCase();
      final String right = i_source.substring(1);

      return left + right;
    }
    else {
      return "";
    }
  }

  public static String getPropertyName(PsiMethod i_method) {
    final String name = i_method.getName();

    if (name.startsWith("get") ||
        name.startsWith("has") ||
        name.startsWith("set"))
    {
      return name.substring(3);
    }

    if (name.startsWith("is")) return name.substring(2);
    return name;
  }

  public static boolean isSetter(PsiMethod i_method,
                                 GetterSetterDefinition gsd)
  {
    if (i_method == null) return false;
    if (i_method.getBody() == null) return false;
    if (nofParameters(i_method) != 1) return false;
    if (!i_method.getName().startsWith("set")) return false;
    String i_methodNameTrail = i_method.getName().substring("set".length());
    if (i_methodNameTrail.length() == 0) return false;
    /** if setter body is immaterial, it can be empty.  Otherwise, it must contain at least one statement. */
    if (nofStatements(i_method) == 0 &&
        gsd.getSetterBodyCriterion() != GetterSetterDefinition.SETTER_BODY_IMMATERIAL)
    {
      return false;
    }

    boolean nameOK = false, bodyOK = false;
    PsiElement psiElement = null;
    if (nofStatements(i_method) > 0) {
      psiElement = i_method.getBody().getStatements()[nofStatements(i_method) - 1].getChildren()[0];
    }
    PsiAssignmentExpression assignment = null;
    String lExpression = null;
    String parameterName = null;
    if (psiElement instanceof PsiAssignmentExpression) {
      assignment = (PsiAssignmentExpression)psiElement;
      lExpression = ((assignment.getLExpression()).getText()).replaceFirst("this.", "");
      parameterName = (i_method.getParameterList()).getParameters()[0].getName();
    }

    String fieldName = fieldName(propertyNameFromMethodTrail(i_methodNameTrail), i_method.getProject());
    switch (gsd.getSetterNameCriterion()) {
      case GetterSetterDefinition.SETTER_NAME_CORRECT_PREFIX:
        nameOK = true;
        break;
      case GetterSetterDefinition.SETTER_NAME_MATCHES_FIELD:
        nameOK = lExpression != null &&
                 lExpression.equals(fieldName);
        break;
    }
    switch (gsd.getSetterBodyCriterion()) {
      case GetterSetterDefinition.SETTER_BODY_IMMATERIAL:
        bodyOK = true;
        break;
      case GetterSetterDefinition.SETTER_BODY_SETS_FIELD:
        if (assignment != null &&
            nameIsWellFormed(i_methodNameTrail) &&
            assignment.getOperationSign().getText().equals("="))
//                    PsiJavaToken.EQ == assignment.getOperationSign().getTokenType()    )
        {
          PsiExpression rExpressionRaw = assignment.getRExpression();
          bodyOK = rExpressionRaw != null &&
                   parameterName.equals(rExpressionRaw.getText()) &&
                   lExpression != null &&
                   lExpression.equals(fieldName);
        }
        break;
    }
    return nameOK && bodyOK;
  }

  private static boolean nameIsWellFormed(String i_methodNameTrail) {
    final boolean methodNameIsTooShort = i_methodNameTrail.length() == 0;
    if (methodNameIsTooShort) {
      return false;
    }

    final char firstCandidateMemberChar = i_methodNameTrail.charAt(0);
    return !Character.isLowerCase(firstCandidateMemberChar);
  }


  private static String fieldName(String i_propertyName, Project project) {
    JavaCodeStyleManager i_codeStyleManager = JavaCodeStyleManager.getInstance(project);
    return i_codeStyleManager.propertyNameToVariableName(i_propertyName, VariableKind.FIELD);
  }

  public static int nofParameters(PsiMethod i_method) {
    return i_method.getParameterList().getParameters().length;
  }

  private static int nofStatements(PsiMethod i_method) {
    return i_method.getBody().getStatements().length;
  }

  private static boolean isAbstract(PsiMethod i_method) {
    return null == i_method.getBody();
  }

  private static boolean isReturnStatement(final PsiStatement i_statement) {
    return i_statement instanceof PsiReturnStatement;
  }
}
