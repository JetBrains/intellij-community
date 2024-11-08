package de.plushnikov.intellij.plugin.handler;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
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
import static java.util.function.Predicate.not;

public class LombokGetterHandler extends BaseLombokHandler {

  @Override
  protected void processClass(@NotNull PsiClass psiClass) {
    final Map<PsiField, PsiMethod> fieldMethodMap = new HashMap<>();
    for (PsiField psiField : psiClass.getFields()) {
      PsiMethod propertyGetter =
        PropertyUtilBase.findPropertyGetter(psiClass, psiField.getName(), psiField.hasModifierProperty(PsiModifier.STATIC), false);

      if (null != propertyGetter) {
        final PsiField setterField = findFieldIfMethodIsSimpleGetter(propertyGetter);
        if (null != setterField && setterField.equals(psiField)) {
          fieldMethodMap.put(psiField, propertyGetter);
        }
      }
    }

    processIntern(fieldMethodMap, psiClass, LombokClassNames.GETTER);
  }

  public static @Nullable PsiField findFieldIfMethodIsSimpleGetter(@NotNull PsiMethod method) {
    final PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) {
      return null;
    }
    final PsiStatement @NotNull [] methodStatements =
      Arrays.stream(methodBody.getStatements()).filter(not(PsiEmptyStatement.class::isInstance)).toArray(PsiStatement[]::new);
    if (methodStatements.length != 1) {
      return null;
    }
    final PsiReturnStatement returnStatement = tryCast(methodStatements[0], PsiReturnStatement.class);
    if (returnStatement == null) {
      return null;
    }
    final PsiReferenceExpression targetRef = tryCast(
      PsiUtil.skipParenthesizedExprDown(returnStatement.getReturnValue()), PsiReferenceExpression.class);
    if (targetRef == null) {
      return null;
    }
    final @Nullable PsiExpression qualifier = targetRef.getQualifierExpression();
    final @Nullable PsiThisExpression thisExpression = tryCast(qualifier, PsiThisExpression.class);
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(method, PsiClass.class);
    if (psiClass == null) {
      return null;
    }
    if (qualifier != null) {
      if (thisExpression == null) {
        return null;
      }
      else if (thisExpression.getQualifier() != null) {
        if (!thisExpression.getQualifier().isReferenceTo(psiClass)) {
          return null;
        }
      }
    }
    final @Nullable String fieldIdentifier = targetRef.getReferenceName();
    if (fieldIdentifier == null) {
      return null;
    }

    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
    final PsiField field = psiClass.findFieldByName(fieldIdentifier, false);
    if (field == null
        || !field.isWritable()
        || isMethodStatic != field.hasModifierProperty(PsiModifier.STATIC)
        || !field.getType().equals(method.getReturnType())) {
      return null;
    }

    //Check lombok would generate the same method name (e.g. for boolean methods prefixed with "is")
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(field);
    final String lombokMethodName = LombokUtils.getGetterName(field, accessorsInfo);
    if (!method.getName().equals(lombokMethodName)) {
      return null;
    }
    return field;
  }
}
