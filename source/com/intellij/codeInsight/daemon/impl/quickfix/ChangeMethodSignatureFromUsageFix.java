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
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.findUsages.FindUsagesUtil;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScopeCache;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;
import java.util.*;

public class ChangeMethodSignatureFromUsageFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CastMethodParametersFix");

  private final PsiMethod myTargetMethod;
  private final PsiExpression[] myExpressions;
  private final PsiSubstitutor mySubstitutor;
  private final PsiElement myContext;
  private ParameterInfo[] myNewParametersInfo;

  private ChangeMethodSignatureFromUsageFix(PsiMethod targetMethod, PsiExpression[] expressions, PsiSubstitutor substitutor, PsiElement context) {
    myTargetMethod = targetMethod;
    myExpressions = expressions;
    mySubstitutor = substitutor;
    myContext = context; LOG.assertTrue(targetMethod != null);
  }

  public String getText() {
    return MessageFormat.format("Change signature of ''{0}'' to ''{1}({2})''",
        new Object[]{
          HighlightUtil.formatMethod(myTargetMethod),
          myTargetMethod.getName(),
          formatTypesList(myNewParametersInfo, myContext),
        });
  }

  private static String formatTypesList(ParameterInfo[] infos, PsiElement context) {
    String result = "";
    try {
      for (int i = 0; i < infos.length; i++) {
        ParameterInfo info = infos[i];
        final PsiType type = info.getTypeWrapper().getType(context);
        if (!result.equals("")) {
          result += ", ";
        }
        result += type.getPresentableText();
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return result;
  }

  public String getFamilyName() {
    return "Change method signature from usage";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!myTargetMethod.isValid()) return false;
    for (int i = 0; i < myExpressions.length; i++) {
      PsiExpression expression = myExpressions[i];
      if (!expression.isValid()) return false;
    }

    myNewParametersInfo = getNewParametersInfo(myExpressions, myTargetMethod, mySubstitutor);
    return myNewParametersInfo != null;
  }

  public void invoke(final Project project, Editor editor, final PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;

    final PsiMethod method = SuperMethodWarningUtil.checkSuperMethod(myTargetMethod, "refactor");
    if (method == null) return;
    if (!CodeInsightUtil.prepareFileForWrite(method.getContainingFile())) return;

    final FindUsagesOptions options = new FindUsagesOptions(project, SearchScopeCache.getInstance(project));
    options.isImplementingMethods = true;
    options.isMethodsUsages = true;
    options.isOverridingMethods = true;
    options.isUsages = true;
    options.isSearchInNonJavaFiles = false;
    final UsageInfo[][] usages = new UsageInfo[1][1];
    final Runnable runnable = new Runnable() {
          public void run() {
            usages[0] = FindUsagesUtil.findUsages(method, options);
          }
        };
    String progressTitle = "Searching For Usages...";
    if (!ApplicationManager.getApplication().runProcessWithProgressSynchronously(runnable, progressTitle, true, project)) return;

    if (usages[0].length <= 1) {
      final ChangeSignatureProcessor processor = new ChangeSignatureProcessor(
                            project,
                            method,
                            false, null,
                            method.getName(),
                            method.getReturnType(),
                            myNewParametersInfo,
                            false,
                            BaseRefactoringProcessor.EMPTY_CALLBACK);
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        processor.testRun();
      }
      else {
        processor.run(null);
      }
      
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          QuickFixAction.spoilDocument(project, file);
        }
      });
    }
    else {
      final List<ParameterInfo> parameterInfos = Arrays.asList(myNewParametersInfo);
      final ChangeSignatureDialog.Callback callback = new ChangeSignatureDialog.Callback() {
            public void run(final ChangeSignatureDialog dialog) {
              new ChangeSignatureProcessor(
                      project,
                      method,
                      false, dialog.getVisibility(),
                      dialog.getMethodName(),
                      dialog.getReturnType(),
                      dialog.getParameters(),
                      null,
                      dialog.isPreviewUsages(),
                      new Runnable() {
                        public void run() {
                          dialog.close(DialogWrapper.OK_EXIT_CODE);
                        }
                      }).run(null);
            }
          };
      ChangeSignatureDialog dialog = new ChangeSignatureDialog(project, method, false, callback);
      dialog.setParameterInfos(parameterInfos);
      dialog.show();
    }
  }

  private static ParameterInfo[] getNewParametersInfo(PsiExpression[] expressions,
                                                      PsiMethod targetMethod,
                                                      PsiSubstitutor substitutor) {
    final PsiParameter[] parameters = targetMethod.getParameterList().getParameters();
    List<ParameterInfo> result = new ArrayList<ParameterInfo>();
    if (expressions.length < parameters.length) {
      // find which parameters to remove
      int ei = 0;
      int pi = 0;

      while (ei < expressions.length && pi < parameters.length) {
        PsiExpression expression = expressions[ei];
        final PsiParameter parameter = parameters[pi];
        final PsiType paramType = substitutor.substitute(parameter.getType());
        if (TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression)) {
          result.add(new ParameterInfo(pi, parameter.getName(), paramType));
          pi++;
          ei++;
        }
        else {
          pi++;
        }
      }
      if (result.size() != expressions.length) return null;
    }
    else if (expressions.length > parameters.length) {
      // find which parameters to introduce and where
      int ei = 0;
      int pi = 0;
      Set<String> existingNames = new HashSet<String>();
      for (int j = 0; j < parameters.length; j++) {
        PsiParameter parameter = parameters[j];
        existingNames.add(parameter.getName());
      }
      while (ei < expressions.length || pi < parameters.length) {
        PsiExpression expression = ei < expressions.length ? expressions[ei] : null;
        final PsiParameter parameter = pi < parameters.length ? parameters[pi] : null;
        PsiType paramType = parameter == null ? null : substitutor.substitute(parameter.getType());
        final boolean parameterAssignable = paramType != null && (expression == null || TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression));
        if (parameterAssignable) {
          result.add(new ParameterInfo(pi, parameter.getName(), paramType));
          pi++;
          ei++;
        }
        else if (expression != null) {
          PsiType exprType = RefactoringUtil.getTypeByExpression(expression);
          if (exprType == null) return null;
          final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(expression.getProject());
          String name = suggestUniqueParameterName(codeStyleManager, expression, exprType, existingNames);
          result.add(new ParameterInfo(-1, name, exprType, expression.getText()));
          ei++;
        }
      }
      if (result.size() != expressions.length) return null;
    }
    else {
      //parameter type changed
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        PsiExpression expression = expressions[i];
        final PsiType paramType = substitutor.substitute(parameter.getType());
        if (TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression)) {
          result.add(new ParameterInfo(i, parameter.getName(), paramType));
        }
        else {
          PsiType exprType = RefactoringUtil.getTypeByExpression(expression);
          if (exprType == null) return null;
          result.add(new ParameterInfo(i, parameter.getName(), exprType));
        }
      }
      // do not perform silly refactorings
      boolean isSilly = true;
      for (int i = 0; i < result.size(); i++) {
        PsiParameter parameter = parameters[i];
        final PsiType paramType = substitutor.substitute(parameter.getType());
        ParameterInfo parameterInfo = result.get(i);
        String typeText = parameterInfo.getTypeText();
        if (!paramType.equalsToText(typeText)) {
          isSilly = false;
          break;
        }
      }
      if (isSilly) return null;
    }
    return result.toArray(new ParameterInfo[result.size()]);
  }

  private static String suggestUniqueParameterName(final CodeStyleManager codeStyleManager,
                                                              PsiExpression expression,
                                                              final PsiType exprType,
                                                              Set<String> existingNames) {
    int suffix =0;
    final SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, expression, exprType);
    final String[] names = nameInfo.names;
    while (true) {
      for (int i = 0; i < names.length; i++) {
        String name = names[i];
        name += suffix == 0 ? "" : ""+suffix;
        if (existingNames.add(name)) {
          return name;
        }
      }
      suffix++;
    }
  }

  public static void registerIntentions(final ResolveResult[] candidates,
                                        PsiExpressionList list,
                                        HighlightInfo highlightInfo, TextRange fixRange) {
    if (candidates == null || candidates.length == 0) return;
    final PsiExpression[] expressions = list.getExpressions();
    for (int i = 0; i < candidates.length; i++) {
      ResolveResult candidate = candidates[i];
      registerIntention(expressions, highlightInfo, fixRange, candidate, list);
    }
  }

  private static void registerIntention(PsiExpression[] expressions,
                                        HighlightInfo highlightInfo,
                                        TextRange fixRange, ResolveResult candidate, PsiElement context) {
    PsiMethod method = (PsiMethod)candidate.getElement();
    PsiSubstitutor substitutor = candidate.getSubstitutor();
    if (method.getManager().isInProject(method)) {
      QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, new ChangeMethodSignatureFromUsageFix(method, expressions, substitutor, context));
    }
  }

  public boolean startInWriteAction() {
    return false;
  }
}
