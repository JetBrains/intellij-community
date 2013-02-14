package com.jetbrains.python.documentation.doctest;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: ktisha
 */
public class PyDocstringLanguageInjector implements LanguageInjector {
  @Override
  public void getLanguagesToInject(@NotNull final PsiLanguageInjectionHost host, @NotNull final InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (host instanceof PyStringLiteralExpression && PyDocumentationSettings.getInstance(host.getProject()).analyzeDoctest) {
      final PyDocStringOwner
        docStringOwner = PsiTreeUtil.getParentOfType(host, PyDocStringOwner.class);
      if (docStringOwner != null && host.equals(docStringOwner.getDocStringExpression())) {
        int start = 0;
        int end = host.getTextLength() - 1;
        final String text = host.getText();
        final List<String> strings = StringUtil.split(text, "\n", false);

        boolean gotExample = false;

        int currentPosition = 0;
        int maxPosition = text.length();
        boolean endsWithSlash = false;
        for (String string : strings) {
          final String trimmedString = string.trim();
          if (!trimmedString.startsWith(">>>") && !trimmedString.startsWith("...") && gotExample && start < end) {
            gotExample = false;
            if (!endsWithSlash)
              injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(), TextRange.create(start, end),  null, null);
          }
          if (endsWithSlash) {
            endsWithSlash = false;
            injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(),
                                              TextRange.create(start, getEndOffset(currentPosition, string, maxPosition)),  null, null);
          }

          if (trimmedString.startsWith(">>>")) {
            if (trimmedString.endsWith("\\"))
              endsWithSlash = true;

            if (!gotExample)
              start = currentPosition;

            gotExample = true;
            end = getEndOffset(currentPosition, string, maxPosition);
          }
          else if (trimmedString.startsWith("...") && gotExample) {
            if (trimmedString.endsWith("\\"))
              endsWithSlash = true;

            end = getEndOffset(currentPosition, string, maxPosition);
          }
          currentPosition += string.length();
        }
        if (gotExample && start < end)
          injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(), TextRange.create(start, end),  null, null);
      }
    }
  }

  private int getEndOffset(int start, String s, int maxPosition) {
    int end;
    int length = s.length();
    if (s.trim().endsWith("\"\"\"") || s.trim().endsWith("'''"))
      length -= 3;
    else if (start + length == maxPosition && (s.trim().endsWith("\"") || s.trim().endsWith("'")))
      length -= 1;

    end = start + length;
    if (s.endsWith("\n"))
      end -= 1;
    return end;
  }
}
