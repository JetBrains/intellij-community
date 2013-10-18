/*
 * @author max
 */
package com.jetbrains.python.refactoring;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * User : ktisha
 */
public class PyNameSuggestionProvider implements NameSuggestionProvider {
  public SuggestedNameInfo getSuggestedNames(PsiElement element, PsiElement nameSuggestionContext, Set<String> result) {
    if (!(element instanceof PyElement)) return null;
    final String name = ((PyElement)element).getName();
    if (name == null) return null;

    if (element instanceof PyClass) {
      result.add(toCamelCase(name, true));
    }
    else if (element instanceof PyFunction || element instanceof PyParameter) {
      result.add(name.toLowerCase());
    }
    else {
      result.add(name.toLowerCase());
      final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
      if (assignmentStatement != null) return null;
      result.add(name.toUpperCase());
      result.add(toCamelCase(name, false));
    }
    return SuggestedNameInfo.NULL_INFO;
  }

  @NotNull
  protected String toCamelCase(@NotNull final String name, boolean uppercaseFirstLetter) {
    final List<String> strings = StringUtil.split(name, "_");
    if (strings.size() > 0) {
      final StringBuilder buf = new StringBuilder();
      String str = strings.get(0).toLowerCase();
      if (uppercaseFirstLetter) str = StringUtil.capitalize(str);
      buf.append(str);
      for (int i = 1; i < strings.size(); i++) {
        buf.append(StringUtil.capitalize(strings.get(i).toLowerCase()));
      }
      return buf.toString();
    }
    return name;
  }
}
