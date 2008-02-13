package com.intellij.refactoring.rename;

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaNameSuggestionProvider implements NameSuggestionProvider {
  @Nullable
  public SuggestedNameInfo getSuggestedNames(final PsiElement element, final PsiElement nameSuggestionContext, List<String> result) {
    String initialName = UsageViewUtil.getShortName(element);
    SuggestedNameInfo info = suggestNamesForElement(element);

    String parameterName = null;
    if (nameSuggestionContext != null) {
      final PsiElement nameSuggestionContextParent = nameSuggestionContext.getParent();
      if (nameSuggestionContextParent != null && nameSuggestionContextParent.getParent() instanceof PsiExpressionList) {
        final PsiExpressionList expressionList = (PsiExpressionList)nameSuggestionContextParent.getParent();
        final PsiElement parent = expressionList.getParent();
        if (parent instanceof PsiCallExpression) {
          final PsiMethod method = ((PsiCallExpression)parent).resolveMethod();
          if (method != null) {
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            final PsiExpression[] expressions = expressionList.getExpressions();
            for (int i = 0; i < expressions.length; i++) {
              PsiExpression expression = expressions[i];
              if (expression == nameSuggestionContextParent) {
                if (i < parameters.length) {
                  parameterName = parameters[i].getName();
                }
                break;
              }
            }
          }
        }
      }
    }
    final String[] strings = info != null ? info.names : ArrayUtil.EMPTY_STRING_ARRAY;
    ArrayList<String> list = new ArrayList<String>(Arrays.asList(strings));
    final String properlyCased = suggestProperlyCasedName(element);
    if (!list.contains(initialName)) {
      list.add(0, initialName);
    }
    else {
      int i = list.indexOf(initialName);
      list.remove(i);
      list.add(0, initialName);
    }
    if (properlyCased != null && !properlyCased.equals(initialName)) {
      list.add(1, properlyCased);
    }
    if (parameterName != null && !list.contains(parameterName)) {
      list.add(parameterName);
    }
    ContainerUtil.removeDuplicates(list);
    result.addAll(list);
    return info;
  }

  @Nullable
  public Collection<LookupItem> completeName(final PsiElement element, final PsiElement nameSuggestionContext, final String prefix) {
    if (element instanceof PsiVariable) {
      PsiVariable var = (PsiVariable)element;
      VariableKind kind = JavaCodeStyleManager.getInstance(element.getProject()).getVariableKind(var);
      Set<LookupItem> set = new LinkedHashSet<LookupItem>();
      JavaCompletionUtil.completeVariableNameForRefactoring(element.getProject(), set, prefix, var.getType(), kind);

      if (prefix.length() == 0) {
        List<String> suggestedNames = new ArrayList<String>();
        getSuggestedNames(element, nameSuggestionContext, suggestedNames);
        for (String suggestedName : suggestedNames) {
          LookupItemUtil.addLookupItem(set, suggestedName);
        }
      }

    }
    return null;
  }

  @Nullable
  private static String suggestProperlyCasedName(PsiElement psiElement) {
    if (!(psiElement instanceof PsiNamedElement)) return null;
    String name = ((PsiNamedElement)psiElement).getName();
    if (name == null) return null;
    if (psiElement instanceof PsiVariable) {
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(psiElement.getProject());
      final VariableKind kind = codeStyleManager.getVariableKind((PsiVariable)psiElement);
      final String prefix = codeStyleManager.getPrefixByVariableKind(kind);
      if (name.startsWith(prefix)) {
        name = name.substring(prefix.length());
      }
      final String[] words = NameUtil.splitNameIntoWords(name);
      if (kind == VariableKind.STATIC_FINAL_FIELD) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
          String word = words[i];
          if (i > 0) buffer.append('_');
          buffer.append(word.toUpperCase());
        }
        return buffer.toString();
      }
      else {
        StringBuilder buffer = new StringBuilder(prefix);
        for (int i = 0; i < words.length; i++) {
          String word = words[i];
          final boolean prefixRequiresCapitalization = prefix.length() > 0 && !StringUtil.endsWithChar(prefix, '_');
          if (i > 0 || prefixRequiresCapitalization) {
            buffer.append(StringUtil.capitalize(word));
          }
          else {
            buffer.append(StringUtil.decapitalize(word));
          }
        }
        return buffer.toString();
      }

    }
    return name;
  }

  @Nullable
  private static SuggestedNameInfo suggestNamesForElement(final PsiElement element) {
    PsiVariable var = null;
    if (element instanceof PsiVariable) {
      var = (PsiVariable)element;
    }
    else if (element instanceof PsiIdentifier) {
      PsiIdentifier identifier = (PsiIdentifier)element;
      if (identifier.getParent() instanceof PsiVariable) {
        var = (PsiVariable)identifier.getParent();
      }
    }

    if (var == null) return null;

    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(element.getProject());
    VariableKind variableKind = codeStyleManager.getVariableKind(var);
    return codeStyleManager.suggestVariableName(variableKind, null, var.getInitializer(), var.getType());
  }

}
