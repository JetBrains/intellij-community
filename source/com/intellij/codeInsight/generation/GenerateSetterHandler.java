package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.j2ee.j2eeDom.ejb.CmpField;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;

public class GenerateSetterHandler extends GenerateGetterSetterHandlerBase {

  public GenerateSetterHandler() {
    super(CodeInsightBundle.message("generate.setter.fields.chooser.title"));
  }

  protected Object[] generateMemberPrototypes(PsiClass aClass, Object original) throws IncorrectOperationException {
    if (original instanceof PsiField) {
      PsiField field = (PsiField)original;
      if (field.hasModifierProperty(PsiModifier.FINAL)) {
        return PsiElement.EMPTY_ARRAY;
      }

      PsiMethod setMethod = PropertyUtil.generateSetterPrototype(field);
      PsiMethod existing = field.getContainingClass().findMethodBySignature(setMethod, false);
      if (existing != null) {
        return PsiElement.EMPTY_ARRAY;
      }
      return new PsiElement[]{setMethod};
    }
    else if (original instanceof CmpField) {
      CmpField field = (CmpField)original;

      final PsiManager psiManager = aClass.getManager();
      final PsiElementFactory factory = psiManager.getElementFactory();
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(psiManager.getProject());

      final PsiType objectType = PsiType.getJavaLangObject(psiManager, aClass.getResolveScope());

      final String methodName = PropertyUtil.suggestSetterName(field.getName());

      final PsiMethod[] methods = aClass.getMethods();
      for (PsiMethod method : methods) {
        if (method.getName().equals(methodName) &&
            method.getParameterList().getParameters().length == 1 &&
            method.getReturnType() == PsiType.VOID) {
          return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }
      }

      final CmpFieldTypeExpression expression = new CmpFieldTypeExpression(psiManager);
      String parameterName = codeStyleManager.propertyNameToVariableName(field.getName(), VariableKind.PARAMETER);

      final PsiMethod method = factory.createMethod(methodName, objectType);
      method.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, true);
      method.getBody().delete();
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter parameter = (PsiParameter)parameterList.add(factory.createParameter(parameterName, objectType));
      method.getReturnTypeElement().replace(factory.createTypeElement(PsiType.VOID));

      TemplateBuilder builder = new TemplateBuilder(method);
      builder.replaceElement(parameter.getTypeElement(), expression);
      TemplateGenerationInfo info = new TemplateGenerationInfo(builder.buildTemplate(), method);

      return new Object[]{info};
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}