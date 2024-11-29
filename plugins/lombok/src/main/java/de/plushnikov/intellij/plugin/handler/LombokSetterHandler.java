package de.plushnikov.intellij.plugin.handler;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static com.intellij.util.ObjectUtils.tryCast;

public class LombokSetterHandler extends BaseLombokHandler {

  @Override
  protected void processClass(@NotNull PsiClass psiClass) {
    final Map<PsiField, PsiMethod> fieldMethodMap = new HashMap<>();
    for (PsiField psiField : psiClass.getFields()) {
      PsiMethod propertySetter =
        PropertyUtilBase.findPropertySetter(psiClass, psiField.getName(), psiField.hasModifierProperty(PsiModifier.STATIC), false);

      if (null != propertySetter) {
        final PsiField setterField = findFieldIfMethodIsSimpleSetter(propertySetter);
        if (null != setterField && setterField.equals(psiField)) {
          fieldMethodMap.put(psiField, propertySetter);
        }
      }
    }

    processIntern(fieldMethodMap, psiClass, LombokClassNames.SETTER);
  }

  public static @Nullable PsiField findFieldIfMethodIsSimpleSetter(@NotNull PsiMethod method) {
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return null;

    final PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) {
      return null;
    }
    final PsiStatement @NotNull [] methodStatements =
      Arrays.stream(methodBody.getStatements()).filter(Predicate.not(PsiEmptyStatement.class::isInstance)).toArray(PsiStatement[]::new);
    if (methodStatements.length != 1) {
      return null;
    }
    final PsiExpressionStatement assignmentStatement = tryCast(methodStatements[0], PsiExpressionStatement.class);
    if (assignmentStatement == null) {
      return null;
    }
    final PsiAssignmentExpression assignment = tryCast(assignmentStatement.getExpression(), PsiAssignmentExpression.class);
    if (assignment == null || assignment.getOperationTokenType() != JavaTokenType.EQ) {
      return null;
    }
    final PsiReferenceExpression sourceRef =
      tryCast(PsiUtil.skipParenthesizedExprDown(assignment.getRExpression()), PsiReferenceExpression.class);
    if (sourceRef == null || sourceRef.getQualifierExpression() != null) {
      return null;
    }
    final @Nullable String paramIdentifier = sourceRef.getReferenceName();
    if (paramIdentifier == null) {
      return null;
    }

    final PsiParameter parameter = method.getParameterList().getParameters()[0];
    if (!paramIdentifier.equals(parameter.getName())) {
      return null;
    }
    final PsiReferenceExpression targetRef = tryCast(assignment.getLExpression(), PsiReferenceExpression.class);
    if (targetRef == null) {
      return null;
    }
    final @Nullable PsiExpression qualifier = targetRef.getQualifierExpression();
    final @Nullable PsiThisExpression thisExpression = tryCast(qualifier, PsiThisExpression.class);
    if (qualifier != null) {
      if (thisExpression == null) {
        return null;
      }
      else if (thisExpression.getQualifier() != null) {
        if (!thisExpression.getQualifier().isReferenceTo(containingClass)) {
          return null;
        }
      }
    }
    final @Nullable String fieldIdentifier = targetRef.getReferenceName();
    if (fieldIdentifier == null) {
      return null;
    }
    if (qualifier == null
        && paramIdentifier.equals(fieldIdentifier)) {
      return null;
    }

    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
    final PsiField field = containingClass.findFieldByName(fieldIdentifier, false);
    if (field == null
        || !field.isWritable()
        || isMethodStatic != field.hasModifierProperty(PsiModifier.STATIC)
        || !field.getType().equals(parameter.getType())) {
      return null;
    }

    //Check lombok would generate same method name (e.g. for boolean methods prefixed with "is")
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(field);
    final String lombokMethodName = LombokUtils.getSetterName(field, accessorsInfo);
    if (!method.getName().equals(lombokMethodName)) {
      return null;
    }
    return field;
  }
}
