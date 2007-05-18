package com.intellij.refactoring.util;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;

/**
 * @author ven
 */
public class InlineUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.refactoring.util.InlineUtil");

  private InlineUtil() {}

  public static PsiExpression inlineVariable(PsiVariable variable, PsiExpression initializer, PsiJavaCodeReferenceElement ref)
    throws IncorrectOperationException {
    PsiManager manager = initializer.getManager();

    PsiClass thisClass = RefactoringUtil.getThisClass(initializer);
    PsiClass refParent = RefactoringUtil.getThisClass(ref);
    final PsiType varType = variable.getType();
    initializer = RefactoringUtil.convertInitializerToNormalExpression(initializer, varType);

    ChangeContextUtil.encodeContextInfo(initializer, false);
    PsiExpression expr = (PsiExpression)ref.replace(initializer);
    PsiType exprType = expr.getType();
    if (exprType != null && !varType.equals(exprType)) {
      boolean matchedTypes = false;
      //try explicit type arguments
      final PsiElementFactory elementFactory = manager.getElementFactory();
      if (expr instanceof PsiCallExpression && ((PsiCallExpression)expr).getTypeArguments().length == 0) {
        final JavaResolveResult resolveResult = ((PsiCallExpression)initializer).resolveMethodGenerics();
        final PsiElement resolved = resolveResult.getElement();
        if (resolved instanceof PsiMethod) {
          final PsiTypeParameter[] typeParameters = ((PsiMethod)resolved).getTypeParameters();
          if (typeParameters.length > 0) {
            final PsiCallExpression copy = (PsiCallExpression)expr.copy();
            for (final PsiTypeParameter typeParameter : typeParameters) {
              final PsiType substituted = resolveResult.getSubstitutor().substitute(typeParameter);
              if (substituted == null) break;
              copy.getTypeArgumentList().add(elementFactory.createTypeElement(substituted));
            }
            if (varType.equals(copy.getType())) {
              ((PsiCallExpression)expr).getTypeArgumentList().replace(copy.getTypeArgumentList());
              matchedTypes = true;
            }
          }
        }
      }

      if (!matchedTypes) {
        if (varType instanceof PsiEllipsisType && ((PsiEllipsisType)varType).getComponentType().equals(exprType)) { //convert vararg to array

          final PsiExpressionList argumentList = PsiTreeUtil.getParentOfType(expr, PsiExpressionList.class);
          LOG.assertTrue(argumentList != null);
          final PsiExpression[] arguments = argumentList.getExpressions();

          @NonNls final StringBuilder builder = new StringBuilder("new ");
          builder.append(exprType.getCanonicalText());
          builder.append("[]{");
          builder.append(StringUtil.join(Arrays.asList(arguments), new Function<PsiExpression, String>() {
            public String fun(final PsiExpression expr) {
              return expr.getText();
            }
          }, ","));
          builder.append('}');

          expr.replace(manager.getElementFactory().createExpressionFromText(builder.toString(), argumentList));

        } else {
          //try cast
          PsiTypeCastExpression cast = (PsiTypeCastExpression)elementFactory.createExpressionFromText("(t)a", null);
          PsiTypeElement castTypeElement = cast.getCastType();
          assert castTypeElement != null;
          castTypeElement.replace(variable.getTypeElement());
          final PsiExpression operand = cast.getOperand();
          assert operand != null;
          operand.replace(expr);
          PsiExpression exprCopy = (PsiExpression)expr.copy();
          cast = (PsiTypeCastExpression)expr.replace(cast);
          if (!RedundantCastUtil.isCastRedundant(cast)) {
            expr = cast;
          }
          else {
            PsiElement toReplace = cast;
            while (toReplace.getParent() instanceof PsiParenthesizedExpression) {
              toReplace = toReplace.getParent();
            }
            expr = (PsiExpression)toReplace.replace(exprCopy);
          }
        }
      }
    }

    ChangeContextUtil.clearContextInfo(initializer);

    PsiThisExpression thisAccessExpr = null;
    if (Comparing.equal(thisClass, refParent))

    {
      thisAccessExpr = RefactoringUtil.createThisExpression(manager, null);
    }

    else

    {
      if (!(thisClass instanceof PsiAnonymousClass)) {
        thisAccessExpr = RefactoringUtil.createThisExpression(manager, thisClass);
      }
    }

    return (PsiExpression)ChangeContextUtil.decodeContextInfo(expr, thisClass, thisAccessExpr);
  }

  public static void tryToInlineArrayCreationForVarargs(final PsiExpression expr) {
    if (expr instanceof PsiNewExpression && ((PsiNewExpression)expr).getArrayInitializer() != null) {
      if (expr.getParent() instanceof PsiExpressionList) {
        final PsiExpressionList exprList = (PsiExpressionList)expr.getParent();
        if (exprList.getParent() instanceof PsiCall) {
          if (isSafeToInlineVarargsArgument((PsiCall)exprList.getParent())) {
            inlineArrayCreationForVarargs(((PsiNewExpression)expr));
          }
        }
      }
    }
  }

  public static void inlineArrayCreationForVarargs(final PsiNewExpression arrayCreation) {
    PsiExpressionList argumentList = (PsiExpressionList)arrayCreation.getParent();
    if (argumentList == null) return;
    PsiExpression[] args = argumentList.getExpressions();
    PsiArrayInitializerExpression arrayInitializer = arrayCreation.getArrayInitializer();
    try {
      if (arrayInitializer == null) {
        arrayCreation.delete();
        return;
      }

      PsiExpression[] initializers = arrayInitializer.getInitializers();
      if (initializers.length > 0) {
        argumentList.addRange(initializers[0], initializers[initializers.length - 1]);
      }
      args[args.length - 1].delete();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static boolean isSafeToInlineVarargsArgument(PsiCall expression) {
    final JavaResolveResult resolveResult = expression.resolveMethodGenerics();
    PsiElement element = resolveResult.getElement();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    if (element instanceof PsiMethod && ((PsiMethod)element).isVarArgs()) {
      PsiMethod method = (PsiMethod)element;
      PsiParameter[] parameters = method.getParameterList().getParameters();
      PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList != null) {
        PsiExpression[] args = argumentList.getExpressions();
        if (parameters.length == args.length) {
          PsiExpression lastArg = args[args.length - 1];
          PsiParameter lastParameter = parameters[args.length - 1];
          PsiType lastParamType = lastParameter.getType();
          LOG.assertTrue(lastParamType instanceof PsiEllipsisType);
          if (lastArg instanceof PsiNewExpression) {
            final PsiType lastArgType = lastArg.getType();
            if (lastArgType != null && substitutor.substitute(((PsiEllipsisType)lastParamType).toArrayType()).isAssignableFrom(lastArgType)) {
              PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)lastArg).getArrayInitializer();
              PsiExpression[] initializers = arrayInitializer != null ? arrayInitializer.getInitializers() : PsiExpression.EMPTY_ARRAY;
              if (isSafeToFlatten(expression, method, initializers)) {
                return true;
              }
            }
          }
        }
      }
    }

    return false;
  }

  private static boolean isSafeToFlatten(PsiCall callExpression, PsiMethod oldRefMethod, PsiExpression[] arrayElements) {
    PsiCall copy = (PsiCall)callExpression.copy();
    PsiExpressionList copyArgumentList = copy.getArgumentList();
    LOG.assertTrue(copyArgumentList != null);
    PsiExpression[] args = copyArgumentList.getExpressions();
    try {
      args[args.length - 1].delete();
      if (arrayElements.length > 0) {
        copyArgumentList.addRange(arrayElements[0], arrayElements[arrayElements.length - 1]);
      }
      return copy.resolveMethod() == oldRefMethod;
    }
    catch (IncorrectOperationException e) {
      return false;
    }
  }
}
