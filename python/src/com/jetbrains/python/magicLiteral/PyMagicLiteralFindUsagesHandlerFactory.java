package com.jetbrains.python.magicLiteral;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * Handles find usage for magic literals.
 * <strong>Install it</strong> as {@link FindUsagesHandlerFactory#EP_NAME}
 * @author Ilya.Kazakevich
 */
public class PyMagicLiteralFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  @Override
  public boolean canFindUsages(@NotNull final PsiElement element) {
    return PyMagicLiteralTools.isMagicLiteral(element);
  }

  @Nullable
  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull final PsiElement element, final boolean forHighlightUsages) {
    return new MyFindUsagesHandler(element);
  }

  private static class MyFindUsagesHandler extends FindUsagesHandler {
    MyFindUsagesHandler(final PsiElement element) {
      super(element);
    }

    @Nullable
    @Override
    protected Collection<String> getStringsToSearch(final PsiElement element) {
      return Collections.singleton(((StringLiteralExpression)element).getStringValue());
    }
  }
}
