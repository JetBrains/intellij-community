package com.jetbrains.python.documentation;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.rest.RestLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class DocStringInjector implements LanguageInjector {
  @Override
  public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (!(host instanceof PyStringLiteralExpression)) return;
    if (!PyDocumentationSettings.getInstance(host.getProject()).isReSTFormat(host.getContainingFile())) return;
    if (!PyUtil.isDocString((PyStringLiteralExpression)host)) return;
    List<TextRange> ranges = ((PyStringLiteralExpression)host).getStringValueTextRanges();
    injectionPlacesRegistrar.addPlace(RestLanguage.INSTANCE, ranges.get(0), null, null);
  }
}
