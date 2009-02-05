/*
 * User: anna
 * Date: 02-Feb-2009
 */
package com.intellij.refactoring.replaceConstructorWithBuilder.usageInfo;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.replaceConstructorWithBuilder.ParameterData;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.Map;

public class ReplaceConstructorWithSettersChainInfo extends FixableUsageInfo {
  private final String  myBuilderClass;
  private final Map<String, ParameterData> myParametersMap;

  public ReplaceConstructorWithSettersChainInfo(PsiNewExpression constructorReference, String builderClass, Map<String, ParameterData> parametersMap) {
    super(constructorReference);
    myBuilderClass = builderClass;
    myParametersMap = parametersMap;
  }

  public void fixUsage() throws IncorrectOperationException {
    final PsiNewExpression expr = (PsiNewExpression)getElement();
    if (expr != null) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();
      final PsiMethod constructor = expr.resolveConstructor();
      if (constructor != null) {
        StringBuffer buf = new StringBuffer();
        final PsiExpressionList argumentList = expr.getArgumentList();
        if (argumentList != null) {
          final PsiExpression[] args = argumentList.getExpressions();
          final PsiParameter[] parameters = constructor.getParameterList().getParameters();

          final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(constructor.getProject());
          for (int i = 0; i < constructor.getParameterList().getParametersCount(); i++) {
            String arg = args[i].getText();
            if (parameters[i].isVarArgs()) {
              for(int ia = i + 1; ia < args.length; ia++) {
                arg += ", " + args[ia].getText();
              }
            }

            final String pureParamName = styleManager.variableNameToPropertyName(parameters[i].getName(), VariableKind.PARAMETER);
            final ParameterData data = myParametersMap.get(pureParamName);
            if (!Comparing.strEqual(arg, data.getDefaultValue()) || data.isInsertSetter()) {
              buf.append(data.getSetterName()).append("(").append(arg).append(").");
            }
          }

          final PsiExpression settersChain = elementFactory.createExpressionFromText(
            "new " + myBuilderClass + "()." + buf.toString() + "create" + StringUtil.capitalize(constructor.getName()) + "()",
            null);

          expr.replace(settersChain);
        }
      }
    }
  }
}