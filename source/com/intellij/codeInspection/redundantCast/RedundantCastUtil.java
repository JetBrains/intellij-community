/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Mar 24, 2002
 * Time: 6:08:14 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.redundantCast;

import com.intellij.psi.*;
import com.intellij.psi.search.PsiBaseElementProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RedundantCastUtil {
  public static List<PsiTypeCastExpression> getRedundantCasts(PsiElement where) {
    final ArrayList<PsiTypeCastExpression> result = new ArrayList<PsiTypeCastExpression>();
    PsiElementProcessor<PsiTypeCastExpression> processor = new PsiBaseElementProcessor<PsiTypeCastExpression>() {
      public boolean execute(PsiTypeCastExpression element) {
        result.add(element);
        return true;
      }
    };
    where.accept(new MyVisitor(processor));
    return result;
  }

  private static class MyVisitor extends PsiRecursiveElementVisitor {
    private final PsiElementProcessor<PsiTypeCastExpression> myProcessor;
    private Set<PsiTypeCastExpression> myFoundCasts = new HashSet<PsiTypeCastExpression>();

    public MyVisitor(PsiElementProcessor<PsiTypeCastExpression> processor) {
      myProcessor = processor;
    }

    private void addToResults(PsiTypeCastExpression typeCast){
      if (!isTypeCastSemantical(typeCast)) {
        if (myFoundCasts.add(typeCast)) {
          myProcessor.execute(typeCast);
        }
      }
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitElement(expression);
    }

    public void visitConditionalExpression(PsiConditionalExpression expression) {
      // Do not go inside conditional expression because branches are required to be exactly the same type, not assignable.
    }

    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      processPossibleTypeCast(expression.getRExpression(), expression.getLExpression().getType());
      super.visitAssignmentExpression(expression);
    }

    public void visitVariable(PsiVariable variable) {
      processPossibleTypeCast(variable.getInitializer(), variable.getType());
      super.visitVariable(variable);
    }

    public void visitBinaryExpression(PsiBinaryExpression expression) {
      PsiExpression rExpr = deParenthesize(expression.getROperand());
      PsiExpression lExpr = deParenthesize(expression.getLOperand());

      if (rExpr != null && lExpr != null) {
        if (lExpr instanceof PsiTypeCastExpression) {
          PsiTypeCastExpression typeCast = (PsiTypeCastExpression)lExpr;
          PsiExpression operand = typeCast.getOperand();
          if (operand != null && operand.getType() != null) {
            if (expression.getOperationSign().getTokenType() != JavaTokenType.PLUS ||
                !typeCast.getCastType().getType().equalsToText("java.lang.String") ||
                operand.getType().equalsToText("java.lang.String")) {
              addToResults(typeCast);
            }
          }
        }
        if (rExpr instanceof PsiTypeCastExpression) {
          addToResults((PsiTypeCastExpression)rExpr);
        }
      }
      super.visitBinaryExpression(expression);
    }

    private void processPossibleTypeCast(PsiExpression rExpr, PsiType lType) {
      rExpr = deParenthesize(rExpr);
      if (rExpr instanceof PsiTypeCastExpression) {
        PsiExpression castOperand = ((PsiTypeCastExpression)rExpr).getOperand();
        if (castOperand != null) {
          PsiType operandType = castOperand.getType();
          if (operandType != null) {
            if (lType != null && lType.isAssignableFrom(operandType)) {
              addToResults((PsiTypeCastExpression)rExpr);
            }
          }
        }
      }
    }

    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      processCall(expression);

      checkForVirtual(expression);
    }

    private void checkForVirtual(PsiMethodCallExpression methodCall) {
      PsiReferenceExpression methodExpr = methodCall.getMethodExpression();
      PsiExpression qualifier = methodExpr.getQualifierExpression();
      try {
        if (!(qualifier instanceof PsiParenthesizedExpression)) return;
        PsiExpression operand = ((PsiParenthesizedExpression)qualifier).getExpression();
        if (!(operand instanceof PsiTypeCastExpression)) return;
        PsiTypeCastExpression typeCast = (PsiTypeCastExpression)operand;
        PsiExpression castOperand = typeCast.getOperand();
        if (castOperand == null) return;

        PsiType type = castOperand.getType();
        if (type == null) return;
        if (type instanceof PsiPrimitiveType) return;

        final ResolveResult resolveResult = methodExpr.advancedResolve(false);
        PsiMethod targetMethod = (PsiMethod)resolveResult.getElement();
        if (targetMethod == null) return;
        if (targetMethod.hasModifierProperty(PsiModifier.STATIC)) return;

        try {
          PsiManager manager = methodExpr.getManager();
          PsiElementFactory factory = manager.getElementFactory();

          PsiMethodCallExpression newCall = (PsiMethodCallExpression)factory.createExpressionFromText(methodCall.getText(), methodCall);
          PsiExpression newQualifier = newCall.getMethodExpression().getQualifierExpression();
          PsiExpression newOperand = ((PsiTypeCastExpression)((PsiParenthesizedExpression)newQualifier).getExpression()).getOperand();
          newQualifier.replace(newOperand);

          final ResolveResult newResult = newCall.getMethodExpression().advancedResolve(false);
          if (!newResult.isValidResult()) return;
          final PsiMethod newTargetMethod = (PsiMethod)newResult.getElement();
          final PsiType newReturnType = newResult.getSubstitutor().substitute(newTargetMethod.getReturnType());
          final PsiType oldReturnType = resolveResult.getSubstitutor().substitute(targetMethod.getReturnType());
          if (newReturnType.equals(oldReturnType)) {
            if (newTargetMethod.equals(targetMethod)) {
                addToResults(typeCast);
            } else if (newTargetMethod.getSignature(newResult.getSubstitutor()).equals(targetMethod.getSignature(resolveResult.getSubstitutor())) &&
                       !(newTargetMethod.isDeprecated() && !targetMethod.isDeprecated())) { // see SCR11555, SCR14559
              addToResults(typeCast);
            }
          }
          qualifier = ((PsiTypeCastExpression) ((PsiParenthesizedExpression) qualifier).getExpression()).getOperand();
        }
        catch (IncorrectOperationException e) {
          return;
        }
      } finally {
        if (qualifier != null) {
          qualifier.accept(this);
        }
      }
    }

    public void visitNewExpression(PsiNewExpression expression) {
      processCall(expression);
      super.visitNewExpression(expression);
    }

    private void processCall(PsiCallExpression expression){
      PsiMethod oldMethod = null;
      PsiParameter[] methodParms = null;
      boolean[] typeCastCandidates = null;
      boolean hasCandidate = false;



      PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) return;
      PsiExpression[] args = argumentList.getExpressions();
      for (int i = 0; i < args.length; i++) {
        PsiExpression arg = args[i];
        arg = deParenthesize(arg);
        if (arg instanceof PsiTypeCastExpression) {
          if (oldMethod == null){
            oldMethod = expression.resolveMethod();
            if (oldMethod == null) return;
            methodParms = oldMethod.getParameterList().getParameters();
            if (methodParms.length == 0 || methodParms.length > args.length) return;
            typeCastCandidates = new boolean[args.length];
          }

          PsiExpression castOperand = ((PsiTypeCastExpression)arg).getOperand();
          if (castOperand == null) return;
          PsiType operandType = castOperand.getType();
          if (operandType == null) return;
          PsiParameter methodParm = methodParms[Math.min(i, methodParms.length - 1)];
          if (!methodParm.getType().isAssignableFrom(operandType)) continue;

          //Check explicit cast for varargs parameter, see SCR 37199
          if (args.length == methodParms.length) {
            if (PsiType.NULL.equals(operandType) && methodParm.isVarArgs()) continue;
          }

          typeCastCandidates[i] = true;
          hasCandidate = true;
        }
      }

      if (hasCandidate) {
        PsiManager manager = expression.getManager();
        PsiElementFactory factory = manager.getElementFactory();

        ResolveResult newResult;

        try {
          PsiCallExpression newCall = (PsiCallExpression)factory.createExpressionFromText(expression.getText(), expression);
          PsiExpression[] newArgs = newCall.getArgumentList().getExpressions();
          for (int i = newArgs.length - 1; i >= 0; i--) {
            if (typeCastCandidates[i]){
              PsiTypeCastExpression castExpression = (PsiTypeCastExpression)deParenthesize(newArgs[i]);
              PsiExpression castOperand = castExpression.getOperand();
              if (castOperand == null) return;
              castExpression.replace(castOperand);
            }
          }

          newResult = newCall.resolveMethodGenerics();
        }
        catch (IncorrectOperationException e) {
          return;
        }

        if (oldMethod.equals(newResult.getElement()) && newResult.isValidResult()) {
          for(int i = 0; i < args.length; i++){
            PsiExpression arg = deParenthesize(args[i]);
            if (typeCastCandidates[i]){
              addToResults((PsiTypeCastExpression)arg);
            }
          }
        }
      }

      for (int i = 0; i < args.length; i++) {
        PsiExpression arg = args[i];
        if (arg instanceof PsiTypeCastExpression){
          PsiExpression castOperand = ((PsiTypeCastExpression)arg).getOperand();
          castOperand.accept(this);
        }
        else{
          arg.accept(this);
        }
      }
    }

    private PsiExpression deParenthesize(PsiExpression arg) {
      while (arg instanceof PsiParenthesizedExpression) arg = ((PsiParenthesizedExpression) arg).getExpression();
      return arg;
    }

    public void visitTypeCastExpression(PsiTypeCastExpression typeCast) {
      if (!myFoundCasts.contains(typeCast)){
        PsiExpression operand = typeCast.getOperand();
        if (operand == null) return;

        PsiElement expr = deParenthesize(operand);

        if (expr instanceof PsiTypeCastExpression){
          PsiType castType = ((PsiTypeCastExpression)expr).getCastType().getType();
          if (!(castType instanceof PsiPrimitiveType)){
            addToResults((PsiTypeCastExpression)expr);
          }
        } else {
          processAlreadyHasTypeCast(typeCast);
        }
      }

      super.visitTypeCastExpression(typeCast);
    }

    private void processAlreadyHasTypeCast(PsiTypeCastExpression typeCast){
      PsiElement parent = typeCast.getParent();
      while(parent instanceof PsiParenthesizedExpression) parent = parent.getParent();
      if (parent instanceof PsiExpressionList) return; // do not replace in arg lists - should be handled by parent

      if (isTypeCastSemantical(typeCast)) return;

      PsiType toType = typeCast.getCastType().getType();
      PsiType fromType = typeCast.getOperand().getType();
      if (fromType == null || toType == null) return;
      if (parent instanceof PsiReferenceExpression) {
        if (toType instanceof PsiClassType && fromType instanceof PsiPrimitiveType) return; //explicit boxing

        //Check accessibility
        if (fromType instanceof PsiClassType) {
          PsiElement element = ((PsiReferenceExpression)parent).resolve();
          if (!(element instanceof PsiMember)) return;
          PsiClass accessClass = ((PsiClassType)fromType).resolve();
          if (accessClass == null) return;
          if (!parent.getManager().getResolveHelper().isAccessible((PsiMember)element, typeCast, accessClass)) return;
        }
      }

      if (toType.isAssignableFrom(fromType)) {
        addToResults(typeCast);
      }
    }
  }

  public static boolean isTypeCastSemantical(PsiTypeCastExpression typeCast) {
    PsiExpression operand = typeCast.getOperand();
    if (operand != null) {
      PsiType opType = operand.getType();
      PsiType castType = typeCast.getCastType().getType();
      if (castType instanceof PsiPrimitiveType) {
        if (opType instanceof PsiPrimitiveType) {
          return !opType.equals(castType); // let's suppose all not equal primitive casts are necessary
        }
      }
      else if (castType instanceof PsiClassType && ((PsiClassType)castType).hasParameters()) {
        if (opType instanceof PsiClassType && ((PsiClassType)opType).isRaw()) return true;
      }
    }

    return false;
  }
}