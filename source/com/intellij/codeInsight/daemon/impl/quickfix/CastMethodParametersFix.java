/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 13, 2002
 * Time: 3:26:50 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class CastMethodParametersFix extends AddTypeCastFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CastMethodParametersFix");

  private final PsiExpressionList myArgList;
  private final int myIndex;
  private final PsiType myToType;

  private CastMethodParametersFix(PsiExpressionList list, int i, PsiType toType) {
    super(null, null);
    myArgList = list;
    myIndex = i;
    myToType = toType;
  }

  public String getText() {
    if (myArgList.getExpressions().length == 1) {
      return "Cast parameter to '" + HighlightUtil.formatType(myToType) + "'";
    }

    return MessageFormat.format("Cast {0} parameter to ''{1}''",
        new Object[]{
          getNumerical(),
          HighlightUtil.formatType(myToType),
        });
  }

  private String getNumerical() {
    String[] nums = {"1st", "2nd", "3rd"};
    return myIndex < nums.length ? nums[myIndex] : (myIndex + 1) + "th";
  }

  public String getFamilyName() {
    return "Add TypeCast";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return
        myToType != null
        && myToType.isValid()
        && myArgList != null
        && myArgList.getExpressions() != null
        && myArgList.getExpressions().length > myIndex
        && myArgList.getExpressions()[myIndex] != null
        && myArgList.getExpressions()[myIndex].isValid();
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    final PsiExpression expression = myArgList.getExpressions()[myIndex];

    addTypeCast(project, expression, myToType);
  }

  public static void registerCastActions(final CandidateInfo[] candidates, PsiExpressionList list, PsiJavaCodeReferenceElement methodRef, HighlightInfo highlightInfo) {
    if (candidates.length == 0) return;
    final List<CandidateInfo> methodCandidates = new ArrayList<CandidateInfo>(Arrays.asList(candidates));
    final PsiExpression[] expressions = list.getExpressions();
    if (expressions == null || expressions.length == 0) return;
    // filter out not castable candidates
    nextMethod:
    for (int i = methodCandidates.size() - 1; i >= 0; i--) {
      CandidateInfo candidate = methodCandidates.get(i);
      PsiMethod method = (PsiMethod) candidate.getElement();
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (expressions.length != parameters.length) {
        methodCandidates.remove(i);
        continue;
      }
      for (int j = 0; j < parameters.length; j++) {
        PsiParameter parameter = parameters[j];
        PsiExpression expression = expressions[j];
        // check if we can cast to this method
        final PsiType exprType = expression.getType();
        final PsiType parameterType = substitutor.substitute(parameter.getType());
        if (exprType == null
            || parameterType == null
            || !TypeConversionUtil.areTypesConvertible(exprType, parameterType)) {
          methodCandidates.remove(i);
          continue nextMethod;
        }
      }
    }

    if (methodCandidates.size() == 0) return;

    try {
      for (int i = 0; i < expressions.length; i++) {
        PsiExpression expression = expressions[i];
        final PsiType exprType = expression.getType();
        Set<String> suggestedCasts = new THashSet<String>();
        // find to which type we can cast this param to get valid method call
        for (int j = 0; j < methodCandidates.size(); j++) {
          CandidateInfo candidate = methodCandidates.get(j);
          PsiMethod method = (PsiMethod) candidate.getElement();
          PsiSubstitutor substitutor = candidate.getSubstitutor();
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          final PsiType originalParameterType = parameters[i].getType();
          final PsiType parameterType = substitutor.substitute(originalParameterType);
          if (parameterType instanceof PsiWildcardType) continue;
          if(suggestedCasts.contains(parameterType.getCanonicalText())) continue;
          // strict compare since even widening cast may help
          if (Comparing.equal(exprType, parameterType)) continue;
          PsiExpressionList newList = (PsiExpressionList) list.copy();
          final PsiTypeCastExpression castExpression = createCastExpression(expression, methodRef.getProject(), parameterType);
          newList.getExpressions()[i].replace(castExpression);

          final MethodResolverProcessor processor;
          if (method.isConstructor()) {
            processor = new MethodResolverProcessor(method.getContainingClass(), newList, newList);
            PsiScopesUtil.processScope(method.getContainingClass(), processor, /*PsiSubstitutor.UNKNOWN*/substitutor, null, newList);
          }
          else {
            processor = new MethodResolverProcessor(method.getName(), method.getContainingClass(), newList, newList);
            PsiScopesUtil.resolveAndWalk(processor, methodRef, null);
          }

          final ResolveResult[] result = processor.getResult();
          if (result.length == 1 && result[0].isValidResult()) {
            suggestedCasts.add(parameterType.getCanonicalText());
            QuickFixAction.registerQuickFixAction(highlightInfo, new CastMethodParametersFix(list, i, parameterType));
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
