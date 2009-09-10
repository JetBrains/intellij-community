/*
 * User: anna
 * Date: 18-Aug-2009
 */
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThreadLocalConversionRule extends TypeConversionRule {
  private static final Logger LOG = Logger.getInstance("#" + ThreadLocalConversionRule.class.getName());


  @Override
  public TypeConversionDescriptor findConversion(PsiType from,
                                                 PsiType to,
                                                 PsiMember member,
                                                 PsiElement context,
                                                 TypeMigrationLabeler labeler) {
    if (to instanceof PsiClassType && isThreadLocalTypeMigration(from, (PsiClassType)to)) {
      return findDirectConversion(context, to, from, labeler);
    }
    return null;
  }

  private static boolean isThreadLocalTypeMigration(PsiType from, PsiClassType to) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(to);
    final PsiClass threadLocalClass = resolveResult.getElement();

    if (threadLocalClass != null) {
      final String typeQualifiedName = threadLocalClass.getQualifiedName();
      if (!Comparing.strEqual(typeQualifiedName, ThreadLocal.class.getName())) {
        return false;
      }
      final PsiTypeParameter[] typeParameters = threadLocalClass.getTypeParameters();
      if (typeParameters.length != 1) return false;
      final PsiType toTypeParameterValue = resolveResult.getSubstitutor().substitute(typeParameters[0]);
      if (toTypeParameterValue != null) {
        if (from instanceof PsiPrimitiveType) {
          final PsiPrimitiveType unboxedInitialType = PsiPrimitiveType.getUnboxedType(toTypeParameterValue);
          if (unboxedInitialType != null) {
            return TypeConversionUtil.areTypesConvertible(from, unboxedInitialType);
          }
        }
        else {
          return TypeConversionUtil.isAssignable(toTypeParameterValue, from);
        }
      }
    }
    return false;
  }

  @Nullable
  public static TypeConversionDescriptor findDirectConversion(PsiElement context, PsiType to, PsiType from, TypeMigrationLabeler labeler) {
    final PsiClass toTypeClass = PsiUtil.resolveClassInType(to);
    LOG.assertTrue(toTypeClass != null);

    final PsiElement parent = context.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      final IElementType operationSign = ((PsiAssignmentExpression)parent).getOperationTokenType();
      if (operationSign == JavaTokenType.EQ) {
        return new TypeConversionDescriptor("$qualifier$ = $val$", "$qualifier$.set($val$)", (PsiAssignmentExpression)parent);
      }
    }

    if (context instanceof PsiReferenceExpression) {
      return new TypeConversionDescriptor("$qualifier$", "$qualifier$.get()");
    }
    else if (context instanceof PsiBinaryExpression) {
      if (((PsiBinaryExpression)context).getOperationTokenType() == JavaTokenType.EQEQ) {
        return new TypeConversionDescriptor("$qualifier$==$val$", "$qualifier$.get()==$val$");
      }
    }

    if (parent instanceof PsiExpressionStatement) {
      if (context instanceof PsiPostfixExpression) {
        final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)context;
        final String sign = postfixExpression.getOperationSign().getText();

        return new TypeConversionDescriptor("$qualifier$" + sign, "$qualifier$.set(" +
                                                                  getBoxedWrapper(from, to, "$qualifier$.get() " + sign.charAt(0) + " 1",
                                                                                  labeler, context, postfixExpression.getOperand().getText() +
                                                                                           sign.charAt(0) +
                                                                                           " 1") +
                                                                  ")");
      }
      else if (context instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)context;
        final String sign = prefixExpression.getOperationSign().getText();
        final PsiExpression operand = prefixExpression.getOperand();
        return new TypeConversionDescriptor(sign + "$qualifier$", "$qualifier$.set(" +
                                                                  getBoxedWrapper(from, to, "$qualifier$.get() " + sign.charAt(0) + " 1",
                                                                                  labeler, context, operand != null ? operand.getText() +
                                                                                                             sign.charAt(0) +
                                                                                                             " 1" : null) +
                                                                  ")");
      }
      else if (context instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)context;
        final String sign = binaryExpression.getOperationSign().getText();
        final PsiExpression rOperand = binaryExpression.getROperand();
        return new TypeConversionDescriptor("$qualifier$" + sign + "$val$", "$qualifier$.set(" +
                                                                            getBoxedWrapper(from, to,
                                                                                            "$qualifier$.get() " + sign + " $val$)",
                                                                                            labeler, context, rOperand != null
                                                                                                     ? binaryExpression.getLOperand()
                                                                                .getText() + sign + rOperand.getText()
                                                                                                     : null) +
                                                                            ")");
      }
      else if (context instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)context;
        final PsiJavaToken signToken = assignmentExpression.getOperationSign();
        final IElementType operationSign = signToken.getTokenType();
        final String sign = signToken.getText();
        if (operationSign == JavaTokenType.EQ) {
          return new TypeConversionDescriptor("$qualifier$ = $val$", "$qualifier$.set($val$)");
        }
        else {
          final PsiExpression rExpression = assignmentExpression.getRExpression();
          return new TypeConversionDescriptor("$qualifier$" + sign + "$val$", "$qualifier$.set(" +
                                                                              getBoxedWrapper(from, to, "$qualifier$.get() " +
                                                                                                        sign.charAt(0) +
                                                                                                        " $val$", labeler, context,
                                                                                              rExpression != null
                                                                                              ? assignmentExpression.getLExpression()
                                                                                                .getText() +
                                                                                                sign.charAt(0) +
                                                                                                rExpression.getText()
                                                                                              : null) +
                                                                              ")");
        }
      }
    }
    return null;
  }


  private static String getBoxedWrapper(final PsiType from,
                                        final PsiType to,
                                        @NotNull String arg,
                                        TypeMigrationLabeler labeler,
                                        PsiElement context,
                                        @Nullable String tryType) {
    if (from instanceof PsiPrimitiveType) {
      final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(to);
      final PsiClass threadLocalClass = resolveResult.getElement();
      LOG.assertTrue(threadLocalClass != null);
      final PsiTypeParameter[] typeParameters = threadLocalClass.getTypeParameters();
      if (typeParameters.length == 1) {
        final PsiType initial = resolveResult.getSubstitutor().substitute(typeParameters[0]);
        final PsiPrimitiveType unboxedInitialType = PsiPrimitiveType.getUnboxedType(initial);
        if (unboxedInitialType != null) {
          LOG.assertTrue(initial != null);
          if (tryType != null) {
            final PsiType exprType = labeler.getTypeEvaluator().evaluateType(
              JavaPsiFacade.getElementFactory(threadLocalClass.getProject()).createExpressionFromText(tryType, context));
            if (exprType != null && unboxedInitialType.isAssignableFrom(exprType)) {
              return arg;
            }
          }
          return "new " + initial.getPresentableText() + "((" + unboxedInitialType.getCanonicalText() + ")(" + arg + "))";
        }
      }
    }
    return arg;
  }


}