package com.jetbrains.python.codeInsight;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author vlan
 */
public abstract class PyInjectorBase implements MultiHostInjector {
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    registerInjection(registrar, context);
  }

  @NotNull
  @Override
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return PyInjectionUtil.ELEMENTS_TO_INJECT_IN;
  }

  @Nullable
  public abstract Language getInjectedLanguage(@NotNull PsiElement context);

  protected PyInjectionUtil.InjectionResult registerInjection(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    final Language language = getInjectedLanguage(context);
    if (language != null) {
      final PsiElement element = PyInjectionUtil.getLargestStringLiteral(context);
      if (element != null) {
        registrar.startInjecting(language);
        final PyInjectionUtil.InjectionResult result = PyInjectionUtil.registerStringLiteralInjection(element, registrar);
        if (result.isInjected()) {
          registrar.doneInjecting();
        }
        return result;
      }
    }
    return PyInjectionUtil.InjectionResult.EMPTY;
  }
}
