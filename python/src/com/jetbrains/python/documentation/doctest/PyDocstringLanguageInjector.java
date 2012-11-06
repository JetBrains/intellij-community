package com.jetbrains.python.documentation.doctest;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: ktisha
 */
public class PyDocstringLanguageInjector implements LanguageInjector {
  @Override
  public void getLanguagesToInject(@NotNull final PsiLanguageInjectionHost host, @NotNull final InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (host instanceof PyStringLiteralExpression && PyUtil.isDocString((PyExpression)host)) {
      int start = 0;
      int end = host.getTextLength() - 1;
      final String text = host.getText();
      final List<String> strings = StringUtil.split(text, "\n", false);

      boolean gotExample = false;

      int currentPosition = 0;
      for (String string : strings) {
        final String trimmedString = string.trim();
        if (!trimmedString.startsWith(">>>") && !trimmedString.startsWith("...") && gotExample && start < end) {
          gotExample = false;
          injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(), TextRange.create(start, end - 1),  null, null);
        }
        if (trimmedString.startsWith(">>>")) {
          if (!gotExample)
            start = currentPosition;

          gotExample = true;
          end = getEndOffset(currentPosition, string);
        }
        else if (trimmedString.startsWith("...") && gotExample) {
          end = getEndOffset(currentPosition, string);
        }
        currentPosition = currentPosition + string.length();
      }
      if (gotExample && start < end)
        injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(), TextRange.create(start, end - 1),  null, null);
    }
  }

  private int getEndOffset(int start, String s) {
    int end;
    int length = s.length();
    if (s.trim().endsWith("\"\"\"") || s.trim().endsWith("'''"))
      length = length - 3;
    else if (s.trim().endsWith("\"") || s.trim().endsWith("'"))
      length = length - 1;

    end = start + length;
    return end;
  }
}
