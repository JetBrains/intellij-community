package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.j2ee.j2eeDom.ejb.CmpField;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;

public class GenerateGetterHandler extends GenerateGetterSetterHandlerBase {
  public GenerateGetterHandler() {
    super("Select Fields to Generate Getters");
  }

  protected Object[] chooseOriginalMembers(PsiClass aClass, Project project) {
    if (aClass.isInterface()) {
      return PsiElement.EMPTY_ARRAY; // TODO
    }
    return super.chooseOriginalMembers(aClass, project);
  }

  protected Object[] generateMemberPrototypes(PsiClass aClass, Object original) throws IncorrectOperationException {
    if (original instanceof PsiField) {
      PsiField field = (PsiField)original;
      PsiMethod getMethod = PropertyUtil.generateGetterPrototype(field);
      PsiMethod existing = field.getContainingClass().findMethodBySignature(getMethod, false);
      if (existing != null) {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
      }
      return new Object[]{getMethod};
    }
    else if (original instanceof CmpField) {
      CmpField field = (CmpField)original;

      final PsiManager psiManager = aClass.getManager();
      final PsiElementFactory factory = psiManager.getElementFactory();

      final PsiType objectType = PsiType.getJavaLangObject(psiManager, aClass.getResolveScope());
      final String methodName = PropertyUtil.suggestGetterName(field.getName(), objectType);

      final PsiMethod[] methods = aClass.getMethods();
      for (PsiMethod method : methods) {
        if (method.getName().equals(methodName) &&
            method.getParameterList().getParameters().length == 0) {
          return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }
      }

      final PsiMethod method = factory.createMethod(methodName, objectType);
      method.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, true);
      method.getBody().delete();

      TemplateBuilder builder = new TemplateBuilder(method);
      final CmpFieldTypeExpression expression = new CmpFieldTypeExpression(psiManager);
      builder.replaceElement(method.getReturnTypeElement(), expression);
      TemplateGenerationInfo info = new TemplateGenerationInfo(builder.buildTemplate(), method);

      return new Object[]{info};
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}